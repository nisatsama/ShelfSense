"""Evaluate the trained M5 demand forecasting model across stores, categories, and baselines."""

from __future__ import annotations

import json
from pathlib import Path
from typing import Dict

import joblib
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np
import pandas as pd

from src.data.preprocess import ARTIFACTS_DIR, FEATURE_COLUMNS, PROCESSED_DIR
from src.training.train import compute_metrics


def run_evaluation(
    processed_path: Path = PROCESSED_DIR / "m5_features.pkl",
    artifacts_dir: Path = ARTIFACTS_DIR,
) -> Dict:
    df = pd.read_pickle(processed_path)
    model = joblib.load(artifacts_dir / "model.joblib")
    metadata = json.loads((artifacts_dir / "model_metadata.json").read_text(encoding="utf-8"))

    test_df = df[df["split"] == "test"].copy()
    y_true = test_df["sales"].to_numpy(dtype=np.float64)
    y_pred = np.clip(model.predict(test_df[FEATURE_COLUMNS]), 0.0, None)
    test_df["predicted"] = y_pred

    overall = compute_metrics(y_true, y_pred)
    baselines = {
        "Moving Avg 7d": compute_metrics(y_true, test_df["rmean_7"].to_numpy()),
        "Moving Avg 28d": compute_metrics(y_true, test_df["rmean_28"].to_numpy()),
        "Seasonal Naive 7d": compute_metrics(y_true, test_df["lag_7"].to_numpy()),
        metadata["selected_model"]: overall,
    }

    by_category = {
        cat: compute_metrics(grp["sales"].to_numpy(), grp["predicted"].to_numpy())
        for cat, grp in test_df.groupby("cat_id", sort=True)
    }
    by_store = {
        store: compute_metrics(grp["sales"].to_numpy(), grp["predicted"].to_numpy())
        for store, grp in test_df.groupby("store_id", sort=True)
    }

    # Plot 1: Aggregated Daily Actual vs Predicted over the 28-day test horizon
    daily = test_df.groupby("date", as_index=False)[["sales", "predicted"]].sum()
    fig, ax = plt.subplots(figsize=(10, 4.5))
    ax.plot(daily["date"], daily["sales"], label="Actual Sales", marker="o", linewidth=2)
    ax.plot(daily["date"], daily["predicted"], label="Predicted Demand", marker="s", linestyle="--", linewidth=2)
    ax.set_title("ShelfSense M5 Test Horizon (28 Days) — Actual vs Predicted Total Daily Sales")
    ax.set_xlabel("Date")
    ax.set_ylabel("Total Units Sold (Sampled Series)")
    ax.legend()
    ax.grid(True, alpha=0.3)
    fig.autofmt_xdate()
    fig.tight_layout()
    fig.savefig(artifacts_dir / "evaluation_actual_vs_predicted.png", dpi=140)
    plt.close(fig)

    # Plot 2: Model WAPE & MAE Comparison
    names = list(baselines.keys())
    wapes = [baselines[n]["wape"] * 100.0 for n in names]
    maes = [baselines[n]["mae"] for n in names]
    fig, axes = plt.subplots(1, 2, figsize=(11, 4.2))
    axes[0].bar(names, wapes, color=["#94a3b8", "#94a3b8", "#94a3b8", "#2563eb"])
    axes[0].set_title("Test WAPE (%) — Lower is Better")
    axes[0].set_ylabel("WAPE (%)")
    axes[0].tick_params(axis="x", rotation=20)
    axes[1].bar(names, maes, color=["#94a3b8", "#94a3b8", "#94a3b8", "#2563eb"])
    axes[1].set_title("Test MAE (Units) — Lower is Better")
    axes[1].set_ylabel("MAE")
    axes[1].tick_params(axis="x", rotation=20)
    fig.tight_layout()
    fig.savefig(artifacts_dir / "evaluation_model_comparison.png", dpi=140)
    plt.close(fig)

    report = {
        "model_version": metadata["model_version"],
        "selected_model": metadata["selected_model"],
        "overall_test_metrics": overall,
        "baseline_comparison": baselines,
        "by_category": by_category,
        "by_store": by_store,
        "daily_actual_vs_predicted": [
            {
                "date": str(row["date"].date()),
                "actual": round(float(row["sales"]), 2),
                "predicted": round(float(row["predicted"]), 2),
            }
            for _, row in daily.iterrows()
        ],
    }
    report_path = artifacts_dir / "evaluation_report.json"
    report_path.write_text(json.dumps(report, indent=2), encoding="utf-8")
    print(f"[evaluate] Evaluation complete. Overall Test: {overall}")
    print(f"[evaluate] Saved report -> {report_path}")
    return report


def main() -> None:
    run_evaluation()


if __name__ == "__main__":
    main()
