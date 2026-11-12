#!/usr/bin/env python3
"""Build data/manifest.csv from the houseplant archive and the iNat download.

Expected layout:
  data/houseplant_raw/<class folder>/<image>
  data/inat/<class_key>/<observation>_<photo>.jpg
  data/inat/manifest.csv
"""
from __future__ import annotations

import hashlib
import json
import re
import unicodedata
from pathlib import Path

import pandas as pd
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
CLASSES = ROOT / "classes.json"
INAT_MANIFEST = ROOT / "data/inat/manifest.csv"
HOUSEPLANT_DIR = ROOT / "data/houseplant_raw"
OUT = ROOT / "data/manifest.csv"
IMAGE_EXT = {".jpg", ".jpeg", ".jpe", ".jfif", ".png", ".webp", ".bmp"}

# Genus-level classes whose iNat genus also contains another target class.
# Adding iNat photos here would mix e.g. Begonia masoniana into the generic
# Begonia class. The houseplant dataset already has enough images for them.
SKIP_INAT = {
    "begonia__begonia",
    "calateia__calathea",
    "dracaena__dracaena",
}


def norm(value: str) -> str:
    value = unicodedata.normalize("NFD", value)
    value = "".join(c for c in value if unicodedata.category(c) != "Mn")
    return re.sub(r"[^a-z0-9]+", " ", value.lower()).strip()


def valid_image(path: Path) -> bool:
    try:
        with Image.open(path) as im:
            im.verify()
        return True
    except Exception:
        return False


def main() -> None:
    classes = json.loads(CLASSES.read_text())
    by_norm: dict[str, str] = {}
    for c in classes:
        for name in (c["raw_label"], c["display"], c["scientific"], c["key"]):
            by_norm.setdefault(norm(name), c["key"])
        # "Aloe Vera" -> "aloe vera"; "Monstera Deliciosa (Monstera deliciosa)"
        # -> also map the part before the parenthesis.
        if "(" in c["raw_label"]:
            by_norm.setdefault(norm(c["raw_label"].split("(")[0]), c["key"])

    rows = []
    unmatched: dict[str, int] = {}

    if HOUSEPLANT_DIR.exists():
        for path in sorted(HOUSEPLANT_DIR.rglob("*")):
            if not path.is_file() or path.suffix.lower() not in IMAGE_EXT:
                continue
            if not valid_image(path):
                continue
            folder = path.parent.name
            key = by_norm.get(norm(folder))
            if key is None:
                # Try the first token or the scientific genus.
                for candidate, ckey in by_norm.items():
                    if candidate and (candidate in norm(folder) or norm(folder) in candidate):
                        key = ckey
                        break
            if key is None:
                unmatched[folder] = unmatched.get(folder, 0) + 1
                continue
            rows.append(
                {
                    "path": str(path.relative_to(ROOT)),
                    "class_key": key,
                    "group": "hp-" + hashlib.sha1(str(path).encode()).hexdigest()[:16],
                    "source": "houseplant",
                }
            )
    else:
        print(f"aviso: {HOUSEPLANT_DIR} não existe")

    if INAT_MANIFEST.exists() and INAT_MANIFEST.stat().st_size > 0:
        inat = pd.read_csv(INAT_MANIFEST)
        if not inat.empty:
            for _, r in inat.iterrows():
                if r["class_key"] in SKIP_INAT:
                    continue
                p = ROOT / r["path"]
                if not p.exists() or p.stat().st_size < 1024:
                    continue
                if not valid_image(p):
                    continue
                rows.append(
                    {
                        "path": r["path"],
                        "class_key": r["class_key"],
                        "group": f"obs-{r['observation_id']}",
                        "source": "inat",
                    }
                )

    if not rows:
        raise SystemExit("nenhuma imagem encontrada")

    df = pd.DataFrame(rows).drop_duplicates(subset=["path"]).reset_index(drop=True)
    df.to_csv(OUT, index=False)

    print(f"{len(df)} imagens -> {OUT}")
    print(df.groupby(["class_key", "source"]).size().unstack(fill_value=0).to_string())
    if unmatched:
        print("\nPastas não reconhecidas (adicione um alias em prepare_manifest.py):")
        for folder, n in sorted(unmatched.items(), key=lambda x: -x[1]):
            print(f"  {folder!r}: {n}")

    counts = df.groupby("class_key").size()
    low = counts[counts < 60]
    if len(low):
        print("\nClasses com menos de 60 imagens:")
        for key, n in low.sort_values().items():
            print(f"  {key}: {n}")


if __name__ == "__main__":
    main()
