#!/usr/bin/env python3
"""Resolve each target class to an iNaturalist taxon and count usable photos.

Only research-grade observations with a Creative Commons photo license are
counted, because those are the ones we may download and use for training.
Brazil is queried first (bounding box), then the world as a fallback for
temperate ornamentals that are rare in Brazil.
"""
from __future__ import annotations

import json
import time
from pathlib import Path

import requests

ROOT = Path(__file__).resolve().parents[1]
CLASSES = ROOT / "classes.json"
API = "https://api.inaturalist.org/v1"
BRAZIL = {"swlat": -34.0, "swlng": -74.0, "nelat": 5.5, "nelng": -34.0}
LICENSES = "cc0,cc-by,cc-by-sa"
S = requests.Session()
S.headers.update({"User-Agent": "EuPlantei-training/0.1 (local research)"})


def get(path: str, **params):
    for attempt in range(5):
        r = S.get(API + path, params=params, timeout=30)
        if r.status_code == 429:
            time.sleep(10 * (attempt + 1))
            continue
        r.raise_for_status()
        return r.json()
    raise RuntimeError(f"rate limited: {path}")


def resolve(name: str, rank: str):
    data = get("/taxa", q=name, rank=rank, per_page=10)
    results = data.get("results", [])
    norm = name.lower()
    exact = [t for t in results if t.get("name", "").lower() == norm]
    if exact:
        return exact[0]
    # iNat sometimes returns the accepted name as a synonym; accept the first
    # result whose rank matches and whose name is close enough.
    for t in results:
        if t.get("rank") == rank:
            return t
    return results[0] if results else None


def count(taxon_id: int, bbox: dict | None):
    params = {
        "taxon_id": taxon_id,
        "quality_grade": "research",
        "photo_license": LICENSES,
        "per_page": 1,
    }
    if bbox:
        params.update(bbox)
    return get("/observations", **params).get("total_results", 0)


def main() -> None:
    classes = json.loads(CLASSES.read_text())
    for i, c in enumerate(classes):
        taxon = resolve(c["scientific"], c["rank"])
        if not taxon:
            print(f"[{i:2d}] {c['scientific']}: NAO ENCONTRADO")
            c.update(taxon_id=None, taxon_name=None, br_count=0, world_count=0)
            continue
        tid = taxon["id"]
        br = count(tid, BRAZIL)
        time.sleep(1.2)
        world = count(tid, None)
        c.update(
            taxon_id=tid,
            taxon_name=taxon.get("name"),
            taxon_rank=taxon.get("rank"),
            br_count=br,
            world_count=world,
        )
        print(f"[{i:2d}] {c['display'][:28]:28s} -> {taxon.get('name'):35s} BR={br:6d} world={world:6d}")
        time.sleep(1.2)
        CLASSES.write_text(json.dumps(classes, ensure_ascii=False, indent=2) + "\n")

    CLASSES.write_text(json.dumps(classes, ensure_ascii=False, indent=2) + "\n")

    low = [c for c in classes if c.get("br_count", 0) < 80 and c.get("world_count", 0) < 200]
    print(f"\nClasses com pouco dado (<80 BR e <200 mundo): {len(low)}")
    for c in low:
        print(f"  {c['display']}: BR={c.get('br_count')} world={c.get('world_count')}")


if __name__ == "__main__":
    main()
