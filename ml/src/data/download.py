"""Download and verify the Walmart M5 Forecasting Accuracy dataset.

Supports:
1. Official Kaggle CLI download when KAGGLE_USERNAME/KAGGLE_KEY or ~/.kaggle/kaggle.json is present.
2. Fallback to the public Nixtla M5 dataset archive containing the original M5 CSV files
   (calendar.csv, sell_prices.csv, sales_train_validation.csv).

Raw CSV files are stored locally under ml/data/raw/ and excluded from Git.
"""

from __future__ import annotations

import argparse
import os
import shutil
import subprocess
import sys
import zipfile
from pathlib import Path
from typing import Iterable

import requests

ML_ROOT = Path(__file__).resolve().parents[2]
RAW_DIR = ML_ROOT / "data" / "raw"

REQUIRED_FILES = [
    "calendar.csv",
    "sell_prices.csv",
    "sales_train_validation.csv",
]

# Publicly mirrored M5 archive used by Nixtla's datasetsforecast package
PUBLIC_M5_URLS = [
    "https://github.com/Nixtla/m5-forecasts/raw/main/datasets/m5.zip",
    "https://nixtlacommunity.s3.amazonaws.com/m5/datasets/m5.zip",
]


def _has_required_files(directory: Path) -> bool:
    return all((directory / name).exists() and (directory / name).stat().st_size > 0 for name in REQUIRED_FILES)


def _flatten_extracted_csvs(directory: Path) -> None:
    """Ensure required CSVs live directly in directory even if extracted inside a subfolder."""
    for req in REQUIRED_FILES:
        target = directory / req
        if target.exists():
            continue
        matches = list(directory.rglob(req))
        if matches:
            shutil.move(str(matches[0]), str(target))


def _has_kaggle_credentials() -> bool:
    if os.environ.get("KAGGLE_USERNAME") and os.environ.get("KAGGLE_KEY"):
        return True
    return (Path.home() / ".kaggle" / "kaggle.json").exists()


def _download_via_kaggle(dest_dir: Path) -> bool:
    kaggle_exe = shutil.which("kaggle")
    if not kaggle_exe or not _has_kaggle_credentials():
        return False
    print("[download] Downloading M5 dataset via Kaggle CLI...")
    result = subprocess.run(
        [kaggle_exe, "competitions", "download", "-c", "m5-forecasting-accuracy", "-p", str(dest_dir)],
        capture_output=True,
        text=True,
        check=False,
    )
    if result.returncode != 0:
        print(f"[download] Kaggle CLI failed: {result.stderr.strip()}")
        return False
    zip_path = dest_dir / "m5-forecasting-accuracy.zip"
    if zip_path.exists():
        with zipfile.ZipFile(zip_path, "r") as zf:
            zf.extractall(dest_dir)
        zip_path.unlink(missing_ok=True)
    _flatten_extracted_csvs(dest_dir)
    return _has_required_files(dest_dir)


def _download_via_public_mirror(dest_dir: Path, urls: Iterable[str] = PUBLIC_M5_URLS) -> bool:
    for url in urls:
        print(f"[download] Fetching M5 archive from {url} ...")
        zip_path = dest_dir / "m5.zip"
        try:
            with requests.get(url, stream=True, timeout=120) as resp:
                resp.raise_for_status()
                total = 0
                with open(zip_path, "wb") as f:
                    for chunk in resp.iter_content(chunk_size=1024 * 1024):
                        if chunk:
                            f.write(chunk)
                            total += len(chunk)
            print(f"[download] Downloaded {total / (1024 * 1024):.2f} MB. Extracting...")
            with zipfile.ZipFile(zip_path, "r") as zf:
                zf.extractall(dest_dir)
            zip_path.unlink(missing_ok=True)
            _flatten_extracted_csvs(dest_dir)
            if _has_required_files(dest_dir):
                return True
        except Exception as exc:
            print(f"[download] Failed fetching {url}: {exc}")
            zip_path.unlink(missing_ok=True)
    return False


def download_m5(dest_dir: Path = RAW_DIR, force: bool = False) -> Path:
    dest_dir.mkdir(parents=True, exist_ok=True)
    if not force and _has_required_files(dest_dir):
        print(f"[download] M5 dataset already present in {dest_dir}")
        return dest_dir

    if _download_via_kaggle(dest_dir):
        print(f"[download] Verified M5 dataset via Kaggle in {dest_dir}")
        return dest_dir

    if _download_via_public_mirror(dest_dir):
        print(f"[download] Verified M5 dataset via public archive in {dest_dir}")
        return dest_dir

    raise RuntimeError(
        "Unable to download M5 dataset automatically. "
        "Configure Kaggle credentials (~/.kaggle/kaggle.json or KAGGLE_USERNAME & KAGGLE_KEY) "
        "and run: kaggle competitions download -c m5-forecasting-accuracy -p ml/data/raw"
    )


def main() -> None:
    parser = argparse.ArgumentParser(description="Download Walmart M5 Forecasting dataset.")
    parser.add_argument("--dest", type=Path, default=RAW_DIR)
    parser.add_argument("--force", action="store_true")
    args = parser.parse_args()
    out_dir = download_m5(args.dest, force=args.force)
    for name in REQUIRED_FILES:
        size_mb = (out_dir / name).stat().st_size / (1024 * 1024)
        print(f"  - {name}: {size_mb:.2f} MB")


if __name__ == "__main__":
    main()
