#!/usr/bin/env python3
"""Compare the current houseplant TFLite with the newly trained one on the
same held-out images.  Only classes present in both label files are scored, so
the 12 new classes do not inflate the old model's denominator.
"""
from __future__ import annotations

import argparse
import json
from collections import defaultdict
from pathlib import Path

import numpy as np
import pandas as pd
import tensorflow as tf
from PIL import Image, ImageOps
from torchvision import transforms as T
from tqdm import tqdm

ROOT = Path(__file__).resolve().parents[1]


def load_labels(path: str) -> list[str]:
    return [x.strip() for x in Path(path).read_text().splitlines() if x.strip()]


def make_interp(path: str):
    interp = tf.lite.Interpreter(model_path=path, num_threads=4)
    interp.allocate_tensors()
    return interp, interp.get_input_details()[0], interp.get_output_details()[0]


def infer(interp, inp, out, arr: np.ndarray) -> np.ndarray:
    interp.set_tensor(inp["index"], arr[None, ...])
    interp.invoke()
    probs = interp.get_tensor(out["index"])[0]
    if probs.dtype != np.float32:
        scale, zero = out["quantization"]
        probs = (probs.astype(np.float32) - zero) * scale
    return probs


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--test-csv", required=True)
    ap.add_argument("--current-tflite", required=True)
    ap.add_argument("--current-labels", required=True)
    ap.add_argument("--new-tflite", required=True)
    ap.add_argument("--new-labels", required=True)
    ap.add_argument("--classes", default=str(ROOT / "classes.json"))
    ap.add_argument("--source", default="", help="houseplant|inat (vazio = todos)")
    ap.add_argument("--img-size", type=int, default=224)
    args = ap.parse_args()

    classes = json.loads(Path(args.classes).read_text())
    key_by_raw = {c["raw_label"]: c["key"] for c in classes}

    def mapping(labels):
        return {key_by_raw[label]: i for i, label in enumerate(labels) if label in key_by_raw}

    cur_map = mapping(load_labels(args.current_labels))
    new_map = mapping(load_labels(args.new_labels))
    shared = set(cur_map) & set(new_map)
    print(f"classes em comum: {len(shared)} | antigo: {len(cur_map)} | novo: {len(new_map)}")

    df = pd.read_csv(args.test_csv)
    if args.source:
        df = df[df["source"] == args.source]
    df = df[df["class_key"].isin(shared)].reset_index(drop=True)
    print(f"imagens de teste comparáveis: {len(df)}")

    cur, ci, co = make_interp(args.current_tflite)
    new, ni, no = make_interp(args.new_tflite)
    print("antigo:", ci["shape"], "->", co["shape"])
    print("novo:  ", ni["shape"], "->", no["shape"])

    tf_eval = T.Compose([T.Resize(int(args.img_size * 1.14)), T.CenterCrop(args.img_size), T.ToTensor()])
    stats = {
        "current": {"top1": 0, "top3": 0, "per": defaultdict(lambda: [0, 0])},
        "new": {"top1": 0, "top3": 0, "per": defaultdict(lambda: [0, 0])},
    }

    for _, row in tqdm(df.iterrows(), total=len(df)):
        img = ImageOps.exif_transpose(Image.open(ROOT / row["path"])).convert("RGB")
        arr = (tf_eval(img).numpy() * 255.0).astype(np.float32).transpose(1, 2, 0)
        for name, interp, inp, out, mapping_ in (
            ("current", cur, ci, co, cur_map),
            ("new", new, ni, no, new_map),
        ):
            probs = infer(interp, inp, out, arr)
            top = np.argsort(-probs)[:3]
            target = mapping_[row["class_key"]]
            ok1 = int(top[0] == target)
            ok3 = int(target in top)
            stats[name]["top1"] += ok1
            stats[name]["top3"] += ok3
            stats[name]["per"][row["class_key"]][0] += ok1
            stats[name]["per"][row["class_key"]][1] += 1

    n = len(df)
    print(f"\nantigo: top1={stats['current']['top1']/n:.1%} top3={stats['current']['top3']/n:.1%}")
    print(f"novo:   top1={stats['new']['top1']/n:.1%} top3={stats['new']['top3']/n:.1%}")

    rows = []
    for key in sorted(stats["current"]["per"]):
        c_ok, c_n = stats["current"]["per"][key]
        n_ok, n_n = stats["new"]["per"][key]
        rows.append((key, c_ok / c_n, n_ok / n_n, c_n))
    rows.sort(key=lambda r: r[2] - r[1])
    print("\nMaiores ganhos:")
    for key, c, nw, cnt in rows[-8:][::-1]:
        print(f"  {key:45s} {c:5.1%} -> {nw:5.1%}  (n={cnt})")
    print("\nPiores/menores ganhos:")
    for key, c, nw, cnt in rows[:8]:
        print(f"  {key:45s} {c:5.1%} -> {nw:5.1%}  (n={cnt})")

    pd.DataFrame(rows, columns=["class_key", "current_top1", "new_top1", "n"]).to_csv(
        ROOT / "artifacts/model_comparison.csv", index=False
    )


if __name__ == "__main__":
    main()
