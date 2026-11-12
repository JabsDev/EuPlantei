#!/usr/bin/env python3
"""Evaluate any TFLite classifier on a manifest split.

Works with both the current houseplant model (47 labels, 0..255 input) and the
new exported model (59 labels, 0..255 input).
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
from tqdm import tqdm

ROOT = Path(__file__).resolve().parents[1]


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--tflite", required=True)
    ap.add_argument("--labels", required=True, help="labels.txt in model output order")
    ap.add_argument("--manifest", required=True, help="CSV with path,class_key")
    ap.add_argument("--classes", default=str(ROOT / "classes.json"))
    ap.add_argument("--img-size", type=int, default=224)
    ap.add_argument("--limit", type=int, default=0)
    ap.add_argument("--source", default="", help="houseplant|inat (vazio = todos)")
    ap.add_argument("--input-scale", choices=["unit", "255"], default="255")
    args = ap.parse_args()

    labels = [x.strip() for x in Path(args.labels).read_text().splitlines() if x.strip()]
    classes = json.loads(Path(args.classes).read_text())
    key_by_raw = {c["raw_label"]: c["key"] for c in classes}
    key_by_label = [key_by_raw.get(label) for label in labels]
    present = {k for k in key_by_label if k}
    key_to_idx = {k: i for i, k in enumerate(key_by_label) if k}

    df = pd.read_csv(args.manifest)
    if args.source:
        df = df[df["source"] == args.source]
    df = df[df["class_key"].isin(present)].reset_index(drop=True)
    if args.limit:
        df = df.head(args.limit)

    interp = tf.lite.Interpreter(model_path=args.tflite, num_threads=4)
    interp.allocate_tensors()
    inp = interp.get_input_details()[0]
    out = interp.get_output_details()[0]
    print("input", inp["shape"], inp["dtype"], "output", out["shape"], out["dtype"])

    correct1 = 0
    correct3 = 0
    per_class = defaultdict(lambda: [0, 0])
    for _, row in tqdm(df.iterrows(), total=len(df)):
        path = ROOT / row["path"]
        img = ImageOps.exif_transpose(Image.open(path)).convert("RGB")
        w, h = img.size
        target = int(args.img_size * 1.14)
        scale = target / min(w, h)
        img = img.resize((max(1, round(w * scale)), max(1, round(h * scale))), Image.LANCZOS)
        w, h = img.size
        left = (w - args.img_size) // 2
        top = (h - args.img_size) // 2
        img = img.crop((left, top, left + args.img_size, top + args.img_size))
        arr = np.asarray(img, dtype=np.float32)
        if args.input_scale == "unit":
            arr = arr / 255.0
        arr = arr[None, ...]
        if inp["dtype"] == np.uint8:
            arr = arr.astype(np.uint8)
        elif inp["dtype"] == np.int8:
            arr = (arr - 128).astype(np.int8)
        else:
            arr = arr.astype(np.float32)
        interp.set_tensor(inp["index"], arr)
        interp.invoke()
        probs = interp.get_tensor(out["index"])[0]
        if probs.dtype != np.float32 and out["dtype"] in (np.uint8, np.int8):
            scale_q, zero = out["quantization"]
            probs = (probs.astype(np.float32) - zero) * scale_q
        top = np.argsort(-probs)[:3]
        target = key_to_idx[row["class_key"]]
        ok1 = int(top[0] == target)
        ok3 = int(target in top)
        correct1 += ok1
        correct3 += ok3
        per_class[row["class_key"]][0] += ok1
        per_class[row["class_key"]][1] += 1

    n = len(df)
    print(f"\ntop1={correct1 / n:.4f} top3={correct3 / n:.4f} n={n}")
    rows = []
    for key, (ok, total) in sorted(per_class.items()):
        rows.append((key, ok, total, ok / total))
    rows.sort(key=lambda r: r[3])
    print("\nPiores classes:")
    for key, ok, total, acc in rows[:12]:
        print(f"  {key:45s} {ok:3d}/{total:3d} = {acc:.1%}")


if __name__ == "__main__":
    main()
