#!/usr/bin/env python3
"""Fine-tune a mobile backbone on the EuPlantei species classes.

The manifest is produced by prepare_manifest.py and has columns:
    path,class_key,group,source

Splits are grouped by `group` (one iNaturalist observation never appears in two
splits) and stratified per class.  The checkpoint stores the class order and the
normalization constants so export/eval cannot drift from training.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import math
import random
import time
from collections import Counter, defaultdict
from pathlib import Path

import numpy as np
import pandas as pd
import timm
import torch
import torch.nn as nn
from PIL import Image, ImageOps
from torch.utils.data import DataLoader, Dataset, WeightedRandomSampler
from torchvision import transforms as T
from tqdm import tqdm

ROOT = Path(__file__).resolve().parents[1]
MANIFEST = ROOT / "data/manifest.csv"
CLASSES = ROOT / "classes.json"
ARTIFACTS = ROOT / "artifacts"
IMAGENET_MEAN = (0.485, 0.456, 0.406)
IMAGENET_STD = (0.229, 0.224, 0.225)


def seed_everything(seed: int) -> None:
    random.seed(seed)
    np.random.seed(seed)
    torch.manual_seed(seed)
    torch.cuda.manual_seed_all(seed)


class PlantDataset(Dataset):
    def __init__(self, df: pd.DataFrame, classes: list[str], transform, root: Path):
        self.df = df.reset_index(drop=True)
        self.class_to_idx = {c: i for i, c in enumerate(classes)}
        self.transform = transform
        self.root = root

    def __len__(self) -> int:
        return len(self.df)

    def __getitem__(self, idx: int):
        row = self.df.iloc[idx]
        path = Path(row["path"])
        if not path.is_absolute():
            path = self.root / path
        with Image.open(path) as img:
            img = ImageOps.exif_transpose(img).convert("RGB")
            x = self.transform(img)
        y = self.class_to_idx[row["class_key"]]
        return x, y


def split_data(df: pd.DataFrame, seed: int) -> tuple[pd.DataFrame, pd.DataFrame, pd.DataFrame]:
    """Per-class grouped split: 80% train, 10% val, 10% test."""
    rng = random.Random(seed)
    parts = {"train": [], "val": [], "test": []}
    for class_key, group in df.groupby("class_key", sort=False):
        groups = list(group.groupby("group", sort=False))
        rng.shuffle(groups)
        n = len(groups)
        if n == 1:
            parts["train"].append(groups[0][1])
            continue
        n_val = max(1, round(n * 0.10))
        n_test = max(1, round(n * 0.10)) if n >= 8 else 0
        # Never let val+test eat the training set.
        if n_val + n_test > n - 1:
            n_test = max(0, n - n_val - 1)
        val_groups = groups[:n_val]
        test_groups = groups[n_val:n_val + n_test]
        train_groups = groups[n_val + n_test:]
        if not train_groups:
            train_groups, val_groups = val_groups, train_groups
        parts["train"].extend(g[1] for g in train_groups)
        parts["val"].extend(g[1] for g in val_groups)
        parts["test"].extend(g[1] for g in test_groups)
    return (
        pd.concat(parts["train"], ignore_index=True),
        pd.concat(parts["val"], ignore_index=True) if parts["val"] else df.iloc[0:0],
        pd.concat(parts["test"], ignore_index=True) if parts["test"] else df.iloc[0:0],
    )


def build_transforms(img_size: int):
    train_tf = T.Compose(
        [
            T.RandomResizedCrop(img_size, scale=(0.55, 1.0), ratio=(0.72, 1.38)),
            T.RandomHorizontalFlip(),
            T.RandomRotation(20),
            T.RandomPerspective(distortion_scale=0.18, p=0.30),
            T.ColorJitter(brightness=0.30, contrast=0.30, saturation=0.30, hue=0.08),
            T.RandomApply([T.GaussianBlur(3, sigma=(0.1, 2.0))], p=0.20),
            T.ToTensor(),
            T.Normalize(IMAGENET_MEAN, IMAGENET_STD),
            T.RandomErasing(p=0.25, scale=(0.02, 0.15)),
        ]
    )
    eval_tf = T.Compose(
        [
            T.Resize(int(img_size * 1.14)),
            T.CenterCrop(img_size),
            T.ToTensor(),
            T.Normalize(IMAGENET_MEAN, IMAGENET_STD),
        ]
    )
    return train_tf, eval_tf


def topk_correct(output: torch.Tensor, target: torch.Tensor, k: int) -> int:
    _, pred = output.topk(k, dim=1)
    return int((pred == target.view(-1, 1)).any(dim=1).sum())


def run_epoch(model, loader, criterion, optimizer, scaler, device, train: bool, desc: str):
    model.train(train)
    total_loss = 0.0
    total = 0
    top1 = 0
    top3 = 0
    preds = []
    targets = []
    for x, y in tqdm(loader, desc=desc, leave=False):
        x = x.to(device, non_blocking=True)
        y = y.to(device, non_blocking=True)
        with torch.set_grad_enabled(train):
            with torch.amp.autocast("cuda", enabled=device.type == "cuda"):
                out = model(x)
                loss = criterion(out, y)
            if train:
                optimizer.zero_grad(set_to_none=True)
                scaler.scale(loss).backward()
                scaler.unscale_(optimizer)
                torch.nn.utils.clip_grad_norm_(model.parameters(), 5.0)
                scaler.step(optimizer)
                scaler.update()
        total_loss += float(loss.detach()) * y.numel()
        total += y.numel()
        top1 += topk_correct(out.detach(), y, 1)
        top3 += topk_correct(out.detach(), y, 3)
        preds.append(out.detach().argmax(1).cpu())
        targets.append(y.detach().cpu())
    preds = torch.cat(preds) if preds else torch.empty(0, dtype=torch.long)
    targets = torch.cat(targets) if targets else torch.empty(0, dtype=torch.long)
    return {
        "loss": total_loss / max(1, total),
        "top1": top1 / max(1, total),
        "top3": top3 / max(1, total),
        "preds": preds,
        "targets": targets,
    }


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--manifest", default=str(MANIFEST))
    ap.add_argument("--classes", default=str(CLASSES))
    ap.add_argument("--arch", default="mobilenetv3_large_100.ra_in1k")
    ap.add_argument("--epochs", type=int, default=20)
    ap.add_argument("--freeze-epochs", type=int, default=2)
    ap.add_argument("--batch-size", type=int, default=64)
    ap.add_argument("--img-size", type=int, default=224)
    ap.add_argument("--lr-head", type=float, default=3e-4)
    ap.add_argument("--lr-backbone", type=float, default=3e-5)
    ap.add_argument("--weight-decay", type=float, default=0.05)
    ap.add_argument("--workers", type=int, default=8)
    ap.add_argument("--seed", type=int, default=42)
    ap.add_argument("--limit-per-class", type=int, default=0, help="0 = all; for smoke tests")
    ap.add_argument("--run-name", default="")
    args = ap.parse_args()

    seed_everything(args.seed)
    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    classes_json = json.loads(Path(args.classes).read_text())
    class_keys = [c["key"] for c in classes_json]
    class_meta = {c["key"]: c for c in classes_json}

    df = pd.read_csv(args.manifest)
    df = df[df["class_key"].isin(class_keys)].copy()
    if df.empty:
        raise SystemExit("manifest vazio ou sem classes conhecidas")

    if args.limit_per_class:
        df = (
            df.sample(frac=1.0, random_state=args.seed)
            .groupby("class_key", group_keys=False)
            .head(args.limit_per_class)
            .reset_index(drop=True)
        )

    train_df, val_df, test_df = split_data(df, args.seed)
    print(f"imagens: train={len(train_df)} val={len(val_df)} test={len(test_df)}")
    extra = sorted(set(test_df["class_key"]) - set(class_keys))
    if extra:
        raise SystemExit(f"classes no test fora do manifesto: {extra}")

    run = args.run_name or time.strftime("%Y%m%d-%H%M%S")
    out_dir = ARTIFACTS / run
    out_dir.mkdir(parents=True, exist_ok=True)
    (out_dir / "train.csv").write_text(train_df.to_csv(index=False))
    (out_dir / "val.csv").write_text(val_df.to_csv(index=False))
    (out_dir / "test.csv").write_text(test_df.to_csv(index=False))
    (out_dir / "labels.txt").write_text("\n".join(c["raw_label"] for c in classes_json) + "\n")

    train_tf, eval_tf = build_transforms(args.img_size)
    train_ds = PlantDataset(train_df, class_keys, train_tf, ROOT)
    val_ds = PlantDataset(val_df, class_keys, eval_tf, ROOT)
    test_ds = PlantDataset(test_df, class_keys, eval_tf, ROOT)

    counts = Counter(train_df["class_key"])
    weights = {k: 1.0 / max(1, counts[k]) ** 0.55 for k in class_keys}
    sample_weights = [weights[row["class_key"]] for _, row in train_df.iterrows()]
    sampler = WeightedRandomSampler(sample_weights, num_samples=len(train_df), replacement=True)

    train_loader = DataLoader(
        train_ds, batch_size=args.batch_size, sampler=sampler, num_workers=args.workers,
        pin_memory=True, persistent_workers=args.workers > 0, drop_last=True,
    )
    val_loader = DataLoader(
        val_ds, batch_size=args.batch_size, shuffle=False, num_workers=args.workers,
        pin_memory=True, persistent_workers=args.workers > 0,
    )
    test_loader = DataLoader(
        test_ds, batch_size=args.batch_size, shuffle=False, num_workers=args.workers,
        pin_memory=True,
    )

    model = timm.create_model(args.arch, pretrained=True, num_classes=len(class_keys))
    model.to(device)
    if args.freeze_epochs:
        for p in model.parameters():
            p.requires_grad = False
        for p in model.get_classifier().parameters():
            p.requires_grad = True

    classifier_ids = {id(p) for p in model.get_classifier().parameters()}
    head_params = [p for p in model.parameters() if id(p) in classifier_ids]
    backbone_params = [p for p in model.parameters() if id(p) not in classifier_ids]
    optimizer = torch.optim.AdamW(
        [
            {"params": backbone_params, "lr": args.lr_backbone},
            {"params": head_params, "lr": args.lr_head},
        ],
        weight_decay=args.weight_decay,
    )
    scaler = torch.amp.GradScaler("cuda", enabled=device.type == "cuda")
    criterion = nn.CrossEntropyLoss(label_smoothing=0.10)

    def lr_factor(epoch: int) -> float:
        warm = max(1, args.freeze_epochs + 1)
        if epoch < warm:
            return (epoch + 1) / warm
        p = (epoch - warm) / max(1, args.epochs - warm)
        return 0.5 * (1.0 + math.cos(math.pi * p))

    scheduler = torch.optim.lr_scheduler.LambdaLR(optimizer, lr_factor)
    best_top1 = 0.0
    best_epoch = -1
    bad_epochs = 0
    log_path = out_dir / "train.log"
    history = []

    def save_ckpt(epoch: int, val: dict) -> None:
        torch.save(
            {
                "model": model.state_dict(),
                "arch": args.arch,
                "classes": [c["raw_label"] for c in classes_json],
                "class_keys": class_keys,
                "img_size": args.img_size,
                "mean": IMAGENET_MEAN,
                "std": IMAGENET_STD,
                "epoch": epoch,
                "val_top1": val["top1"],
                "val_top3": val["top3"],
            },
            out_dir / "best.pt",
        )

    for epoch in range(args.epochs):
        if epoch == args.freeze_epochs and args.freeze_epochs:
            for p in model.parameters():
                p.requires_grad = True
            print("backbone liberado para fine-tuning")
        t0 = time.time()
        tr = run_epoch(model, train_loader, criterion, optimizer, scaler, device, True, f"train {epoch}")
        va = run_epoch(model, val_loader, criterion, optimizer, scaler, device, False, f"val {epoch}")
        scheduler.step()
        lrs = [g["lr"] for g in optimizer.param_groups]
        row = {
            "epoch": epoch,
            "train_loss": tr["loss"],
            "train_top1": tr["top1"],
            "train_top3": tr["top3"],
            "val_loss": va["loss"],
            "val_top1": va["top1"],
            "val_top3": va["top3"],
            "lr_backbone": lrs[0],
            "lr_head": lrs[1],
            "seconds": round(time.time() - t0, 1),
        }
        history.append(row)
        print(json.dumps(row, ensure_ascii=False))
        with log_path.open("a") as f:
            f.write(json.dumps(row, ensure_ascii=False) + "\n")
        if va["top1"] > best_top1:
            best_top1 = va["top1"]
            best_epoch = epoch
            bad_epochs = 0
            save_ckpt(epoch, va)
        else:
            bad_epochs += 1
        if bad_epochs >= 6:
            print(f"early stopping na época {epoch}")
            break

    # Reload the best checkpoint and evaluate the held-out test split.
    ckpt = torch.load(out_dir / "best.pt", map_location=device)
    model.load_state_dict(ckpt["model"])
    te = run_epoch(model, test_loader, criterion, optimizer, scaler, device, False, "test")
    print(f"TEST top1={te['top1']:.4f} top3={te['top3']:.4f} loss={te['loss']:.4f}")

    from sklearn.metrics import classification_report, confusion_matrix

    labels = list(range(len(class_keys)))
    report = classification_report(
        te["targets"].numpy(), te["preds"].numpy(), labels=labels, target_names=class_keys,
        digits=4, zero_division=0,
    )
    (out_dir / "classification_report.txt").write_text(report)
    cm = confusion_matrix(te["targets"].numpy(), te["preds"].numpy(), labels=labels)
    pd.DataFrame(cm, index=class_keys, columns=class_keys).to_csv(out_dir / "confusion.csv")
    summary = {
        "arch": args.arch,
        "classes": len(class_keys),
        "train": len(train_df),
        "val": len(val_df),
        "test": len(test_df),
        "best_epoch": best_epoch,
        "best_val_top1": best_top1,
        "test_top1": te["top1"],
        "test_top3": te["top3"],
        "history": history,
    }
    (out_dir / "metrics.json").write_text(json.dumps(summary, indent=2))
    print(f"artefatos: {out_dir}")


if __name__ == "__main__":
    main()
