#!/usr/bin/env bash
# Copy an exported model into the Android assets.
# Usage: ./scripts/install_assets.sh v1-b0
set -euo pipefail
cd "$(dirname "$0")/.."
RUN="${1:?uso: install_assets.sh <run>}"
SRC="artifacts/$RUN/export"
DST="../app/src/main/assets/models"

test -f "$SRC/houseplant_model.tflite"
test -f "$SRC/houseplant_labels.txt"
test -f "$SRC/houseplant_labels_ptbr.json"

cp "$SRC/houseplant_model.tflite" "$DST/houseplant_model.tflite"
cp "$SRC/houseplant_labels.txt" "$DST/houseplant_labels.txt"
cp "$SRC/houseplant_labels_ptbr.json" "$DST/houseplant_labels_ptbr.json"

ls -lh "$DST/houseplant_model.tflite" "$DST/houseplant_labels.txt" "$DST/houseplant_labels_ptbr.json"
