"""Leak-free preprocessing and feature engineering for the Walmart M5 dataset.

Important domain note:
- M5 provides historical daily unit sales (`sales_train_validation.csv`), calendar/events (`calendar.csv`),
  and weekly item-store prices (`sell_prices.csv`).
- It does NOT contain historical on-hand inventory or verified stockout labels; historical sales are used
  as the forecasting target.
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Dict, List, Tuple

import joblib
import numpy as np
import pandas as pd

ML_ROOT = Path(__file__).resolve().parents[2]
RAW_DIR = ML_ROOT / "data" / "raw"
PROCESSED_DIR = ML_ROOT / "data" / "processed"
ARTIFACTS_DIR = ML_ROOT / "artifacts"

FEATURE_COLUMNS = [
    "lag_1",
    "lag_7",
    "lag_14",
    "lag_28",
    "rmean_7",
    "rmean_14",
    "rmean_28",
    "rstd_7",
    "rstd_28",
    "wday",
    "month",
    "day_of_month",
    "is_weekend",
    "has_event",
    "snap_active",
    "sell_price",
    "price_rel_mean",
    "cat_code",
    "store_code",
    "state_code",
]

VAL_DAYS = 28
TEST_DAYS = 28


def validate_raw_files(raw_dir: Path) -> None:
    for fname in ("calendar.csv", "sell_prices.csv", "sales_train_validation.csv"):
        path = raw_dir / fname
        if not path.exists() or path.stat().st_size == 0:
            raise FileNotFoundError(f"Missing or empty M5 dataset file: {path}")


def select_stratified_series(sales_df: pd.DataFrame, max_series: int, seed: int = 42) -> pd.DataFrame:
    """Deterministically select representative active series across all stores and categories."""
    day_cols = [c for c in sales_df.columns if c.startswith("d_")]
    recent_cols = day_cols[-365:]
    # Prefer series with meaningful non-zero activity in the last year so evaluation is informative
    sales_df = sales_df.copy()
    sales_df["_recent_sum"] = sales_df[recent_cols].sum(axis=1)
    active = sales_df[sales_df["_recent_sum"] >= 30].copy()
    if len(active) <= max_series:
        return active.drop(columns=["_recent_sum"])

    groups = active.groupby(["store_id", "cat_id"], sort=True)
    per_group = max(1, max_series // max(1, groups.ngroups))
    sampled_parts = []
    for _, grp in groups:
        take = min(len(grp), per_group)
        sampled_parts.append(grp.sample(n=take, random_state=seed))
    sampled = pd.concat(sampled_parts, ignore_index=True)

    if len(sampled) < max_series:
        remaining = active.loc[~active["id"].isin(sampled["id"])]
        extra = min(max_series - len(sampled), len(remaining))
        if extra > 0:
            sampled = pd.concat([sampled, remaining.sample(n=extra, random_state=seed)], ignore_index=True)

    return sampled.head(max_series).drop(columns=["_recent_sum"]).reset_index(drop=True)


def build_feature_table(
    raw_dir: Path = RAW_DIR,
    max_series: int = 300,
    lookback_days: int = 730,
    seed: int = 42,
) -> Tuple[pd.DataFrame, Dict]:
    validate_raw_files(raw_dir)
    print(f"[preprocess] Loading M5 raw files from {raw_dir}...")
    sales_wide = pd.read_csv(raw_dir / "sales_train_validation.csv")
    calendar = pd.read_csv(raw_dir / "calendar.csv")
    prices = pd.read_csv(raw_dir / "sell_prices.csv")

    # Synthesize canonical series id if absent (some M5 archives omit the redundant id column)
    if "id" not in sales_wide.columns:
        sales_wide["id"] = sales_wide["item_id"].astype(str) + "_" + sales_wide["store_id"].astype(str)

    # Drop duplicate series IDs if any
    sales_wide = sales_wide.drop_duplicates(subset=["id"]).reset_index(drop=True)

    all_day_cols = [c for c in sales_wide.columns if c.startswith("d_")]
    if len(all_day_cols) < (VAL_DAYS + TEST_DAYS + 60):
        raise ValueError(f"Insufficient historical days in M5 file: {len(all_day_cols)}")

    selected_days = all_day_cols[-lookback_days:] if len(all_day_cols) > lookback_days else all_day_cols
    id_cols = ["id", "item_id", "dept_id", "cat_id", "store_id", "state_id"]

    sampled_wide = select_stratified_series(sales_wide[id_cols + selected_days], max_series=max_series, seed=seed)
    print(f"[preprocess] Selected {len(sampled_wide)} real M5 store-item series x {len(selected_days)} days.")

    # Melt to long panel format
    panel = sampled_wide.melt(
        id_vars=id_cols,
        value_vars=selected_days,
        var_name="d",
        value_name="sales",
    )
    panel["sales"] = pd.to_numeric(panel["sales"], errors="coerce").fillna(0.0).clip(lower=0.0)
    panel["day_num"] = panel["d"].str.slice(2).astype(np.int32)

    # Merge calendar metadata (synthesize d_1..d_N key if absent in archive)
    if "d" not in calendar.columns:
        calendar = calendar.reset_index(drop=True)
        calendar["d"] = [f"d_{i}" for i in range(1, len(calendar) + 1)]
    cal_cols = ["d", "date", "wm_yr_wk", "wday", "month", "event_name_1", "snap_CA", "snap_TX", "snap_WI"]
    cal = calendar[cal_cols].drop_duplicates(subset=["d"]).copy()
    cal["date"] = pd.to_datetime(cal["date"])
    cal["day_of_month"] = cal["date"].dt.day.astype(np.int16)
    cal["is_weekend"] = cal["wday"].isin([1, 2]).astype(np.int8)  # M5 wday 1=Sat, 2=Sun
    cal["has_event"] = cal["event_name_1"].notna().astype(np.int8)

    panel = panel.merge(cal, on="d", how="left")

    # State-specific SNAP flag
    panel["snap_active"] = np.where(
        panel["state_id"] == "CA",
        panel["snap_CA"],
        np.where(panel["state_id"] == "TX", panel["snap_TX"], panel["snap_WI"]),
    ).astype(np.int8)

    # Merge sell_prices (filter prices to selected items first for speed & memory)
    used_items = set(sampled_wide["item_id"].unique())
    prices_sub = prices[prices["item_id"].isin(used_items)].drop_duplicates(
        subset=["store_id", "item_id", "wm_yr_wk"]
    )
    panel = panel.merge(prices_sub, on=["store_id", "item_id", "wm_yr_wk"], how="left")

    # Sort strictly by series and time before any lag/rolling or fill!
    panel = panel.sort_values(["id", "day_num"]).reset_index(drop=True)

    # Forward-fill then back-fill missing price within each series, fallback to category median
    panel["sell_price"] = panel.groupby("id")["sell_price"].ffill().bfill()
    cat_median_price = panel.groupby("cat_id")["sell_price"].transform("median")
    panel["sell_price"] = panel["sell_price"].fillna(cat_median_price).fillna(panel["sell_price"].median())

    # Strict temporal split boundaries (no random split!)
    max_day = int(panel["day_num"].max())
    test_start_day = max_day - TEST_DAYS + 1
    val_start_day = test_start_day - VAL_DAYS

    # Compute mean price per series STRICTLY on the training period to prevent leakage
    train_price_means = (
        panel[panel["day_num"] < val_start_day]
        .groupby("id")["sell_price"]
        .mean()
        .rename("_train_price_mean")
    )
    panel = panel.merge(train_price_means, on="id", how="left")
    panel["_train_price_mean"] = panel["_train_price_mean"].fillna(panel["sell_price"]).replace(0, 1.0)
    panel["price_rel_mean"] = (panel["sell_price"] / panel["_train_price_mean"]).astype(np.float32)

    # Engineer leak-free lag and rolling features (strictly shifted by >= 1 day!)
    grouped_sales = panel.groupby("id", sort=False)["sales"]
    for lag in (1, 7, 14, 28):
        panel[f"lag_{lag}"] = grouped_sales.shift(lag).astype(np.float32)

    shifted_sales = grouped_sales.shift(1)
    for window in (7, 14, 28):
        panel[f"rmean_{window}"] = (
            shifted_sales.groupby(panel["id"], sort=False)
            .rolling(window, min_periods=1)
            .mean()
            .reset_index(level=0, drop=True)
            .astype(np.float32)
        )
    for window in (7, 28):
        panel[f"rstd_{window}"] = (
            shifted_sales.groupby(panel["id"], sort=False)
            .rolling(window, min_periods=2)
            .std()
            .reset_index(level=0, drop=True)
            .fillna(0.0)
            .astype(np.float32)
        )

    # Drop initial warm-up rows where lag_28 is NaN so all models train on complete history
    panel = panel.dropna(subset=["lag_28"]).reset_index(drop=True)

    # Deterministic categorical encoders
    cat_categories = sorted(panel["cat_id"].unique().tolist())
    store_categories = sorted(panel["store_id"].unique().tolist())
    state_categories = sorted(panel["state_id"].unique().tolist())

    cat_map = {v: i for i, v in enumerate(cat_categories)}
    store_map = {v: i for i, v in enumerate(store_categories)}
    state_map = {v: i for i, v in enumerate(state_categories)}

    panel["cat_code"] = panel["cat_id"].map(cat_map).astype(np.int16)
    panel["store_code"] = panel["store_id"].map(store_map).astype(np.int16)
    panel["state_code"] = panel["state_id"].map(state_map).astype(np.int16)

    panel["split"] = np.where(
        panel["day_num"] >= test_start_day,
        "test",
        np.where(panel["day_num"] >= val_start_day, "val", "train"),
    )

    preprocessor_artifact = {
        "feature_columns": FEATURE_COLUMNS,
        "cat_map": cat_map,
        "store_map": store_map,
        "state_map": state_map,
        "default_cat_code": 0,
        "default_store_code": 0,
        "default_state_code": 0,
        "median_sell_price": float(panel["sell_price"].median()),
        "split_info": {
            "train_day_min": int(panel.loc[panel["split"] == "train", "day_num"].min()),
            "train_day_max": int(panel.loc[panel["split"] == "train", "day_num"].max()),
            "val_day_min": int(val_start_day),
            "val_day_max": int(test_start_day - 1),
            "test_day_min": int(test_start_day),
            "test_day_max": int(max_day),
            "train_date_range": [
                str(panel.loc[panel["split"] == "train", "date"].min().date()),
                str(panel.loc[panel["split"] == "train", "date"].max().date()),
            ],
            "val_date_range": [
                str(panel.loc[panel["split"] == "val", "date"].min().date()),
                str(panel.loc[panel["split"] == "val", "date"].max().date()),
            ],
            "test_date_range": [
                str(panel.loc[panel["split"] == "test", "date"].min().date()),
                str(panel.loc[panel["split"] == "test", "date"].max().date()),
            ],
            "num_series": int(panel["id"].nunique()),
            "total_rows": int(len(panel)),
            "train_rows": int((panel["split"] == "train").sum()),
            "val_rows": int((panel["split"] == "val").sum()),
            "test_rows": int((panel["split"] == "test").sum()),
        },
    }

    keep_cols = [
        "id", "item_id", "cat_id", "store_id", "state_id", "d", "day_num", "date", "sales", "split"
    ] + FEATURE_COLUMNS
    return panel[keep_cols], preprocessor_artifact


def run_preprocessing(
    raw_dir: Path = RAW_DIR,
    processed_dir: Path = PROCESSED_DIR,
    artifacts_dir: Path = ARTIFACTS_DIR,
    max_series: int = 300,
    lookback_days: int = 730,
) -> Path:
    processed_dir.mkdir(parents=True, exist_ok=True)
    artifacts_dir.mkdir(parents=True, exist_ok=True)

    panel, preprocessor = build_feature_table(
        raw_dir=raw_dir,
        max_series=max_series,
        lookback_days=lookback_days,
    )
    out_path = processed_dir / "m5_features.pkl"
    panel.to_pickle(out_path)
    joblib.dump(preprocessor, artifacts_dir / "preprocessor.joblib")
    (artifacts_dir / "dataset_summary.json").write_text(
        json.dumps(preprocessor["split_info"], indent=2),
        encoding="utf-8",
    )
    print(f"[preprocess] Saved processed panel ({len(panel):,} rows) -> {out_path}")
    print(f"[preprocess] Split summary: {json.dumps(preprocessor['split_info'], indent=2)}")
    return out_path


def main() -> None:
    parser = argparse.ArgumentParser(description="Preprocess Walmart M5 dataset.")
    parser.add_argument("--raw-dir", type=Path, default=RAW_DIR)
    parser.add_argument("--max-series", type=int, default=300)
    parser.add_argument("--lookback-days", type=int, default=730)
    args = parser.parse_args()
    run_preprocessing(
        raw_dir=args.raw_dir,
        max_series=args.max_series,
        lookback_days=args.lookback_days,
    )


if __name__ == "__main__":
    main()
