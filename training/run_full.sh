#!/usr/bin/env bash
# Full pipeline: manifest -> fine-tune -> TFLite export -> evaluation.
# Run from training/ with the venv already created.
set -euo pipefail
cd "$(dirname "$0")"
source .venv/bin/activate

RUN="${RUN:-v1}"
ARCH="${ARCH:-efficientnet_b0.ra_in1k}"
EPOCHS="${EPOCHS:-20}"
BATCH="${BATCH:-64}"
WORKERS="${WORKERS:-8}"

python scripts/prepare_manifest.py
python -u scripts/train.py \
  --arch "$ARCH" \
  --epochs "$EPOCHS" \
  --freeze-epochs 2 \
  --batch-size "$BATCH" \
  --workers "$WORKERS" \
  --lr-backbone 1e-4 \
  --run-name "$RUN"
python -u scripts/export_tflite.py --run "$RUN"
python -u scripts/eval_tflite.py \
  --tflite "artifacts/$RUN/export/houseplant_model.tflite" \
  --labels "artifacts/$RUN/export/houseplant_labels.txt" \
  --manifest "artifacts/$RUN/test.csv"

echo "Pronto: artifacts/$RUN/export/"
