#!/usr/bin/env python3
"""Summarize the training-data licenses for the model card / credits."""
from __future__ import annotations

import json
from collections import Counter
from pathlib import Path

import pandas as pd

ROOT = Path(__file__).resolve().parents[1]
INAT = ROOT / "data/inat/manifest.csv"
OUT = ROOT / "artifacts/ATTRIBUTION.md"


def main() -> None:
    lines = [
        "# Atribuição dos dados de treino",
        "",
        "## Dataset de plantas de interior",
        "",
        "- `kakasher/house-plant-species` — 14.790 imagens, 47 classes, CC BY 3.0.",
        "- Origem declarada no card: imagens coletadas do Bing e curadas manualmente.",
        "",
        "## iNaturalist",
        "",
        "Somente observações `research` com foto CC0, CC BY ou CC BY-SA.",
        "Cada linha do manifesto guarda autor, licença e URL da observação.",
        "",
    ]
    if INAT.exists() and INAT.stat().st_size > 0:
        df = pd.read_csv(INAT)
        # These genus-level classes were downloaded but excluded from training
        # because their iNat genus also contains a sister target class.
        df = df[~df["class_key"].isin({"begonia__begonia", "calateia__calathea", "dracaena__dracaena"})]
        counts = Counter(df["license"].fillna("desconhecida"))
        lines.append("| Licença | Fotos |")
        lines.append("|---|---:|")
        for lic, n in counts.most_common():
            lines.append(f"| {lic} | {n} |")
        lines.append("")
        lines.append(f"Total de fotos do iNaturalist: **{len(df)}**")
        lines.append("")
        lines.append("Amostra de atribuições:")
        lines.append("")
        for _, r in df.drop_duplicates("class_key").head(12).iterrows():
            lines.append(
                f"- {r['class_key']}: {r['attribution']} "
                f"([observação](https://www.inaturalist.org/observations/{r['observation_id']}))"
            )
        lines.append("")
        lines.append(
            "O manifesto completo está em `data/inat/manifest.csv` (não versionado)."
        )
    else:
        lines.append("Manifesto do iNaturalist ainda não gerado.")
    OUT.write_text("\n".join(lines) + "\n")
    print(f"escrito {OUT}")


if __name__ == "__main__":
    main()
