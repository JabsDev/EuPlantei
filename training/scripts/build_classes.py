#!/usr/bin/env python3
"""Builds the target class list for the EuPlantei species model.

The 47 classes of the current houseplant model are kept (same display names),
and the ornamental species that exist in the local knowledge base but are not
covered by that model are added.  Every class maps to an app `speciesId`, so the
existing care-profile lookup keeps working.

Output: training/classes.json
"""
from __future__ import annotations

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT.parent / "app/src/main/assets"

# raw_label -> scientific name used to query iNaturalist.
# Genus-level entries are intentional: the app's speciesId is already generic
# (e.g. "calateia", "samambaia"), and the 47-class model already mixes genera.
INAT_NAME = {
    "African Violet (Saintpaulia ionantha)": ("Saintpaulia ionantha", "species"),
    "Aloe Vera": ("Aloe vera", "species"),
    "Anthurium (Anthurium andraeanum)": ("Anthurium andraeanum", "species"),
    "Areca Palm (Dypsis lutescens)": ("Dypsis lutescens", "species"),
    "Asparagus Fern (Asparagus setaceus)": ("Asparagus setaceus", "species"),
    "Begonia (Begonia spp.)": ("Begonia", "genus"),
    "Bird of Paradise (Strelitzia reginae)": ("Strelitzia reginae", "species"),
    "Birds Nest Fern (Asplenium nidus)": ("Asplenium nidus", "species"),
    "Boston Fern (Nephrolepis exaltata)": ("Nephrolepis exaltata", "species"),
    "Calathea": ("Calathea", "genus"),
    "Cast Iron Plant (Aspidistra elatior)": ("Aspidistra elatior", "species"),
    "Chinese evergreen (Aglaonema)": ("Aglaonema", "genus"),
    "Chinese Money Plant (Pilea peperomioides)": ("Pilea peperomioides", "species"),
    "Christmas Cactus (Schlumbergera bridgesii)": ("Schlumbergera", "genus"),
    "Chrysanthemum": ("Chrysanthemum", "genus"),
    "Ctenanthe": ("Ctenanthe", "genus"),
    "Daffodils (Narcissus spp.)": ("Narcissus", "genus"),
    "Dracaena": ("Dracaena", "genus"),
    "Dumb Cane (Dieffenbachia spp.)": ("Dieffenbachia", "genus"),
    "Elephant Ear (Alocasia spp.)": ("Alocasia", "genus"),
    "English Ivy (Hedera helix)": ("Hedera helix", "species"),
    "Hyacinth (Hyacinthus orientalis)": ("Hyacinthus orientalis", "species"),
    "Iron Cross begonia (Begonia masoniana)": ("Begonia masoniana", "species"),
    "Jade plant (Crassula ovata)": ("Crassula ovata", "species"),
    "Kalanchoe": ("Kalanchoe", "genus"),
    "Lilium (Hemerocallis)": ("Lilium", "genus"),
    "Lily of the valley (Convallaria majalis)": ("Convallaria majalis", "species"),
    "Money Tree (Pachira aquatica)": ("Pachira aquatica", "species"),
    "Monstera Deliciosa (Monstera deliciosa)": ("Monstera deliciosa", "species"),
    "Orchid": ("Phalaenopsis", "genus"),
    "Parlor Palm (Chamaedorea elegans)": ("Chamaedorea elegans", "species"),
    "Peace lily": ("Spathiphyllum wallisii", "species"),
    "Poinsettia (Euphorbia pulcherrima)": ("Euphorbia pulcherrima", "species"),
    "Polka Dot Plant (Hypoestes phyllostachya)": ("Hypoestes phyllostachya", "species"),
    "Ponytail Palm (Beaucarnea recurvata)": ("Beaucarnea recurvata", "species"),
    "Pothos (Ivy arum)": ("Epipremnum aureum", "species"),
    "Prayer Plant (Maranta leuconeura)": ("Maranta leuconeura", "species"),
    "Rattlesnake Plant (Calathea lancifolia)": ("Calathea lancifolia", "species"),
    "Rubber Plant (Ficus elastica)": ("Ficus elastica", "species"),
    "Sago Palm (Cycas revoluta)": ("Cycas revoluta", "species"),
    "Schefflera": ("Schefflera", "genus"),
    "Snake plant (Sanseviera)": ("Dracaena trifasciata", "species"),
    "Tradescantia": ("Tradescantia", "genus"),
    "Tulip": ("Tulipa", "genus"),
    "Venus Flytrap": ("Dionaea muscipula", "species"),
    "Yucca": ("Yucca", "genus"),
    "ZZ Plant (Zamioculcas zamiifolia)": ("Zamioculcas zamiifolia", "species"),
}

# Knowledge-base species that the 47-class model does not cover.
EXTRA = [
    # key, display, speciesId, scientific, rank
    ("rosa", "Rosa", "rosa", "Rosa", "genus"),
    ("jasmim", "Jasmim", "jasmim", "Jasminum", "genus"),
    ("hortensia", "Hortênsia", "hortensia", "Hydrangea macrophylla", "species"),
    ("geranio", "Gerânio", "geranio", "Pelargonium", "genus"),
    ("cravo", "Cravo", "cravo", "Tagetes", "genus"),
    ("lavanda", "Lavanda", "lavanda", "Lavandula angustifolia", "species"),
    ("alecrim", "Alecrim", "alecrim", "Salvia rosmarinus", "species"),
    ("manjericao", "Manjericão", "manjericao", "Ocimum basilicum", "species"),
    ("hortela", "Hortelã", "hortela", "Mentha", "genus"),
    ("peperomia", "Peperômia", "peperomia", "Peperomia", "genus"),
    ("clorofito", "Clorofito", "clorofito", "Chlorophytum comosum", "species"),
    ("girassol", "Girassol", "girassol", "Helianthus annuus", "species"),
]


def slug(value: str) -> str:
    import re
    import unicodedata

    value = unicodedata.normalize("NFD", value)
    value = "".join(c for c in value if unicodedata.category(c) != "Mn")
    return re.sub(r"[^a-z0-9]+", "-", value.lower()).strip("-")


def main() -> None:
    labels = json.loads((ASSETS / "models/houseplant_labels_ptbr.json").read_text())["labels"]
    classes = []
    for raw, friendly in labels.items():
        scientific, rank = INAT_NAME[raw]
        classes.append(
            {
                "key": f"{friendly['speciesId']}__{slug(scientific)}",
                "raw_label": raw,
                "display": friendly["species"],
                "species_id": friendly["speciesId"],
                "scientific": scientific,
                "rank": rank,
                "source": "houseplant",
                "condition": friendly.get("condition", "Condição a avaliar"),
                "healthy": bool(friendly.get("healthy", False)),
            }
        )
    for key, display, species_id, scientific, rank in EXTRA:
        classes.append(
            {
                "key": key,
                "raw_label": display,
                "display": display,
                "species_id": species_id,
                "scientific": scientific,
                "rank": rank,
                "source": "inat",
                "condition": "Condição a avaliar",
                "healthy": False,
            }
        )

    # Unique output labels are required by labels.txt.
    raw_labels = [c["raw_label"] for c in classes]
    assert len(raw_labels) == len(set(raw_labels)), "raw_label duplicado"

    out = ROOT / "classes.json"
    out.write_text(json.dumps(classes, ensure_ascii=False, indent=2) + "\n")
    print(f"{len(classes)} classes -> {out}")
    for i, c in enumerate(classes):
        print(f"{i:2d} {c['raw_label'][:45]:45s} {c['scientific']:30s} {c['rank']}")


if __name__ == "__main__":
    main()
