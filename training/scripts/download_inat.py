#!/usr/bin/env python3
"""Download CC-licensed iNaturalist photos for the target classes.

Strategy:
  * the 47 classes already have the houseplant dataset; iNat adds real-world
    phone photos (up to 150 per class);
  * the 12 knowledge-base species that the current model does not cover get up
    to 300 photos each;
  * Brazil is preferred, the world is used as fallback for temperate plants.

Only research-grade observations with CC0/CC-BY/CC-BY-SA photos are used.
Every downloaded image is recorded in data/inat/manifest.csv with its
attribution, so the app's credits file can be generated later.
"""
from __future__ import annotations

import csv
import io
import json
import re
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path

import requests
from PIL import Image, ImageOps
from tqdm import tqdm

ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / "data/inat"
MANIFEST = ROOT / "data/inat/manifest.csv"
CLASSES = ROOT / "classes.json"
API = "https://api.inaturalist.org/v1"
BRAZIL = {"swlat": -34.0, "swlng": -74.0, "nelat": 5.5, "nelng": -34.0}
LICENSES = {"cc0", "cc-by", "cc-by-sa"}
TARGET_HOUSEPLANT = 150
TARGET_EXTRA = 300
MAX_SIDE = 640

S = requests.Session()
S.headers.update({"User-Agent": "EuPlantei-training/0.1 (local research)"})


def api(path: str, **params):
    for attempt in range(6):
        try:
            r = S.get(API + path, params=params, timeout=60)
        except requests.RequestException as exc:
            if attempt == 5:
                raise
            wait = 5 * (attempt + 1)
            print(f"  conexão caiu ({exc}); tentando de novo em {wait}s", flush=True)
            time.sleep(wait)
            continue
        if r.status_code == 429:
            wait = int(r.headers.get("Retry-After", 15 * (attempt + 1)))
            print(f"  429 em {path}; esperando {wait}s", flush=True)
            time.sleep(wait)
            continue
        r.raise_for_status()
        return r.json()
    raise RuntimeError("iNaturalist rate limit persistente")


def load_done() -> tuple[set[str], set[str]]:
    paths: set[str] = set()
    obs: set[str] = set()
    if MANIFEST.exists():
        with MANIFEST.open(newline="") as f:
            for row in csv.DictReader(f):
                paths.add(row["path"])
                obs.add(f"{row['class_key']}|{row['observation_id']}")
    return paths, obs


def download_one(url: str, dest: Path) -> bool:
    try:
        r = S.get(url, timeout=60)
        r.raise_for_status()
        img = Image.open(io.BytesIO(r.content))
        img = ImageOps.exif_transpose(img).convert("RGB")
        w, h = img.size
        scale = MAX_SIDE / max(w, h)
        if scale < 1:
            img = img.resize((round(w * scale), round(h * scale)), Image.LANCZOS)
        dest.parent.mkdir(parents=True, exist_ok=True)
        img.save(dest, "JPEG", quality=88, optimize=True)
        return True
    except Exception as exc:  # noqa: BLE001 - keep the batch alive
        print(f"  ! falha {url}: {exc}")
        return False


def collect(c: dict, need: int) -> list[dict]:
    """Return candidate observations, Brazil first, then the world."""
    out: list[dict] = []
    seen: set[int] = set()
    for region, bbox in (("br", BRAZIL), ("world", None)):
        page = 1
        while len(out) < need and page <= 12:
            params = {
                "taxon_id": c["taxon_id"],
                "quality_grade": "research",
                "photo_license": ",".join(sorted(LICENSES)),
                "per_page": 50,
                "page": page,
            }
            if bbox:
                params.update(bbox)
            data = api("/observations", **params)
            results = data.get("results", [])
            if not results:
                break
            for ob in results:
                oid = ob["id"]
                if oid in seen:
                    continue
                photos = [
                    p for p in ob.get("photos", [])
                    if p.get("license_code") in LICENSES
                ]
                if not photos:
                    continue
                seen.add(oid)
                p = photos[0]
                # The v1 API returns only the square thumbnail URL; the same S3
                # object also has large/medium/original variants.
                url = p.get("url", "")
                url = re.sub(r"/(square|small|medium|large)\.", "/large.", url)
                if not url:
                    continue
                out.append(
                    {
                        "class_key": c["key"],
                        "observation_id": oid,
                        "photo_id": p["id"],
                        "url": url,
                        "license": p.get("license_code", ""),
                        "attribution": p.get("attribution", ""),
                        "taxon_name": c.get("taxon_name", c["scientific"]),
                        "observed_on": ob.get("observed_on", ""),
                        "place": ob.get("place_guess", ""),
                        "region": region,
                    }
                )
                if len(out) >= need:
                    break
            page += 1
            time.sleep(1.2)
    return out


def main() -> None:
    classes = json.loads(CLASSES.read_text())
    DATA.mkdir(parents=True, exist_ok=True)
    done_paths, done_obs = load_done()
    new_file = not MANIFEST.exists() or MANIFEST.stat().st_size == 0
    fh = MANIFEST.open("a", newline="")
    writer = csv.DictWriter(
        fh,
        fieldnames=[
            "class_key", "observation_id", "photo_id", "path", "url", "license",
            "attribution", "taxon_name", "observed_on", "place", "region",
        ],
    )
    if new_file:
        writer.writeheader()

    for idx, c in enumerate(classes):
        if not c.get("taxon_id"):
            continue
        target = TARGET_HOUSEPLANT if c["source"] == "houseplant" else TARGET_EXTRA
        folder = DATA / c["key"]
        prefix = str(folder.relative_to(ROOT))
        have = len([p for p in done_paths if p.startswith(prefix)])
        need = max(0, target - have)
        if need == 0:
            print(f"[{idx:2d}] {c['display']}: já tem {have}, pulando")
            continue
        print(f"[{idx:2d}] {c['display']}: tem {have}, buscando {need}")
        candidates = collect(c, need + 40)
        candidates = [
            x for x in candidates
            if f"{x['class_key']}|{x['observation_id']}" not in done_obs
        ][:need]
        if not candidates:
            print("  (sem candidatos novos)")
            continue

        futures = {}
        with ThreadPoolExecutor(max_workers=8) as pool:
            for x in candidates:
                dest = folder / f"{x['observation_id']}_{x['photo_id']}.jpg"
                x["path"] = str(dest.relative_to(ROOT))
                futures[pool.submit(download_one, x["url"], dest)] = x
            for fut in tqdm(as_completed(futures), total=len(futures), desc=c["display"][:22]):
                x = futures[fut]
                if fut.result():
                    writer.writerow(x)
                    done_obs.add(f"{x['class_key']}|{x['observation_id']}")
        fh.flush()

    fh.close()
    print(f"manifest: {MANIFEST}")


if __name__ == "__main__":
    main()
