#!/usr/bin/env python3
"""Export the best checkpoint to ONNX and a dynamically quantized TFLite.

The exported model accepts NHWC float32 [1,224,224,3] in 0..255 and returns
softmax probabilities.  That matches AppContainer's houseplant classifier
(`normalizeToUnit = false`), so the Android preprocessing does not need to
change.
"""
from __future__ import annotations

import argparse
import json
import subprocess
from pathlib import Path

import numpy as np
import pandas as pd
import timm
import torch
import torch.nn as nn

ROOT = Path(__file__).resolve().parents[1]
CLASSES = ROOT / "classes.json"


class ExportWrapper(nn.Module):
    def __init__(self, model: nn.Module, mean, std):
        super().__init__()
        self.model = model
        self.register_buffer("mean", torch.tensor(mean).view(1, 3, 1, 1))
        self.register_buffer("std", torch.tensor(std).view(1, 3, 1, 1))

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        x = x / 255.0
        x = (x - self.mean) / self.std
        return torch.softmax(self.model(x), dim=1)


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--run", required=True, help="pasta em artifacts/ com best.pt")
    ap.add_argument("--img-size", type=int, default=0)
    ap.add_argument("--skip-quant", action="store_true")
    ap.add_argument("--variant", choices=["dynamic", "float16", "float32"], default="dynamic")
    args = ap.parse_args()

    run_dir = ROOT / "artifacts" / args.run
    ckpt = torch.load(run_dir / "best.pt", map_location="cpu")
    img_size = args.img_size or ckpt.get("img_size", 224)
    arch = ckpt["arch"]
    class_keys = ckpt["class_keys"]
    classes_json = json.loads(CLASSES.read_text())
    by_key = {c["key"]: c for c in classes_json}

    model = timm.create_model(arch, pretrained=False, num_classes=len(class_keys))
    model.load_state_dict(ckpt["model"])
    model.eval()
    wrapper = ExportWrapper(model, ckpt["mean"], ckpt["std"]).eval()

    export_dir = run_dir / "export"
    export_dir.mkdir(parents=True, exist_ok=True)
    onnx_path = export_dir / "model.onnx"
    dummy = torch.rand(1, 3, img_size, img_size) * 255.0
    torch.onnx.export(
        wrapper,
        dummy,
        onnx_path,
        input_names=["input"],
        output_names=["probabilities"],
        opset_version=17,
        do_constant_folding=True,
        dynamo=False,
    )
    print(f"ONNX: {onnx_path} ({onnx_path.stat().st_size / 1e6:.1f} MB)")

    saved_model = export_dir / "saved_model"
    cmd = [
        "onnx2tf", "-i", str(onnx_path), "-o", str(saved_model),
        "-osd", "-tb", "tf_converter",
    ]
    if not args.skip_quant:
        cmd.append("-odrqt")
    print(" ".join(cmd))
    subprocess.run(cmd, check=True)

    # onnx2tf names the artifacts model_float32.tflite / model_dynamic_range_quant.tflite
    candidates = sorted(saved_model.glob("*.tflite"))
    for c in candidates:
        print(f"  {c.name}: {c.stat().st_size / 1e6:.2f} MB")
    if not candidates:
        raise SystemExit("onnx2tf não gerou TFLite")

    # Prefer the requested variant, then dynamic-range, then float32.
    wanted = {
        "dynamic": "dynamic_range",
        "float16": "float16",
        "float32": "float32",
    }[args.variant]
    tflite = next(
        (c for c in candidates if wanted in c.name),
        next((c for c in candidates if "dynamic_range" in c.name), candidates[0]),
    )
    final = export_dir / "houseplant_model.tflite"
    final.write_bytes(tflite.read_bytes())
    print(f"TFLite final: {final} ({final.stat().st_size / 1e6:.2f} MB)")

    labels = [by_key[k]["raw_label"] for k in class_keys]
    (export_dir / "houseplant_labels.txt").write_text("\n".join(labels) + "\n")
    friendly = {
        "labels": {
            by_key[k]["raw_label"]: {
                "species": by_key[k]["display"],
                "speciesId": by_key[k]["species_id"],
                "condition": by_key[k].get("condition", "Condição a avaliar"),
                "healthy": bool(by_key[k].get("healthy", False)),
            }
            for k in class_keys
        }
    }
    (export_dir / "houseplant_labels_ptbr.json").write_text(
        json.dumps(friendly, ensure_ascii=False, indent=2) + "\n"
    )

    # Quick numerical validation against PyTorch on the test split.
    test_csv = run_dir / "test.csv"
    if test_csv.exists():
        import tensorflow as tf
        from PIL import Image
        from torchvision import transforms as T

        eval_tf = T.Compose(
            [
                T.Resize(int(img_size * 1.14)),
                T.CenterCrop(img_size),
                T.ToTensor(),
            ]
        )
        interp = tf.lite.Interpreter(model_path=str(final))
        interp.allocate_tensors()
        inp = interp.get_input_details()[0]
        out = interp.get_output_details()[0]
        print("TFLite input:", inp["shape"], inp["dtype"], "output:", out["shape"], out["dtype"])

        df = pd.read_csv(test_csv).head(120)
        agree = 0
        max_diff = 0.0
        for _, row in df.iterrows():
            path = ROOT / row["path"]
            img = Image.open(path).convert("RGB")
            x_raw = (eval_tf(img).unsqueeze(0) * 255.0).float()
            with torch.no_grad():
                p_torch = wrapper(x_raw).numpy()[0]
            arr = np.transpose(x_raw.numpy()[0], (1, 2, 0)).astype(np.float32)
            # The TFLite graph expects 0..255 and normalizes internally.
            interp.set_tensor(inp["index"], arr[None, ...])
            interp.invoke()
            p_tflite = interp.get_tensor(out["index"])[0]
            agree += int(p_torch.argmax() == p_tflite.argmax())
            max_diff = max(max_diff, float(np.max(np.abs(p_torch - p_tflite))))
        n = len(df)
        print(f"validação TFLite: top1 igual ao PyTorch em {agree}/{n} ({agree / n:.1%}), max|diff|={max_diff:.5f}")

    print(f"exportado em {export_dir}")


if __name__ == "__main__":
    main()
