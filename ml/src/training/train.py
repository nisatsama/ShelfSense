"""Train and compare forecasting baselines and gradient boosting models on M5 data."""

from __future__ import annotations

import argparse
import json
from datetime import datetime, timezone
from pathlib import Path
from typing import Dict

import joblib
import numpy as np
import pandas as pd
from sklearn.ensemble import HistGradientBoostingRegressor
from sklearn.linear_model import Ridge

from src.data.preprocess import ARTIFACTS_DIR, FEATURE_COLUMNS, PROCESSED_DIR

MODEL_VERSION = "shelfsense-m5-hgbr-1.0.0"


def compute_metrics(y_true: np.ndarray, y_pred: np.ndarray) -> Dict[str, float]:
    y_true = np.asarray(y_true, dtype=np.float64)
    y_pred = np.clip(np.asarray(y_pred, dtype=np.float64), 0.0, None)
    errors = y_pred - y_true
    abs_errors = np.abs(errors)
    mae = float(np.mean(abs_errors))
    rmse = float(np.sqrt(np.mean(errors ** 2)))
    denom = float(np.sum(np.abs(y_true)))
    wape = float(np.sum(abs_errors) / denom) if denom > 0 else 0.0
    bias = float(np.mean(errors))
    return {
        "mae": round(mae, 4),
        "rmse": round(rmse, 4),
        "wape": round(wape, 4),
        "bias": round(bias, 4),
    }


def train_and_select(
    processed_path: Path = PROCESSED_DIR / "m5_features.pkl",
    artifacts_dir: Path = ARTIFACTS_DIR,
) -> Dict:
    artifacts_dir.mkdir(parents=True, exist_ok=True)
    if not processed_path.exists():
        raise FileNotFoundError(f"Processed feature dataset not found: {processed_path}. Run preprocess first.")

    df = pd.read_pickle(processed_path)
    preprocessor = joblib.load(artifacts_dir / "preprocessor.joblib")

    train_df = df[df["split"] == "train"]
    val_df = df[df["split"] == "val"]
    test_df = df[df["split"] == "test"]

    X_train, y_train = train_df[FEATURE_COLUMNS], train_df["sales"].to_numpy(dtype=np.float64)
    X_val, y_val = val_df[FEATURE_COLUMNS], val_df["sales"].to_numpy(dtype=np.float64)
    X_test, y_test = test_df[FEATURE_COLUMNS], test_df["sales"].to_numpy(dtype=np.float64)

    print(
        f"[train] Rows — train: {len(train_df):,}, val: {len(val_df):,}, test: {len(test_df):,}"
    )

    val_results: Dict[str, Dict[str, float]] = {}

    # Baseline 1: 7-day moving average
    val_results["baseline_ma7"] = compute_metrics(y_val, val_df["rmean_7"].to_numpy())
    # Baseline 2: 28-day moving average
    val_results["baseline_ma28"] = compute_metrics(y_val, val_df["rmean_28"].to_numpy())
    # Baseline 3: 7-day seasonal naive (same weekday last week)
    val_results["baseline_seasonal_naive7"] = compute_metrics(y_val, val_df["lag_7"].to_numpy())

    # Candidate 1: Ridge Regression
    ridge = Ridge(alpha=10.0, random_state=42)
    ridge.fit(X_train, y_train)
    val_results["ridge_regression"] = compute_metrics(y_val, ridge.predict(X_val))

    # Candidate 2: HistGradientBoostingRegressor (Poisson loss suits non-negative retail count demand)
    hgbr_params = {
        "loss": "poisson",
        "learning_rate": 0.05,
        "max_iter": 250,
        "max_leaf_nodes": 31,
        "min_samples_leaf": 30,
        "l2_regularization": 1.0,
        "early_stopping": True,
        "validation_fraction": 0.1,
        "random_state": 42,
    }
    hgbr = HistGradientBoostingRegressor(**hgbr_params)
    hgbr.fit(X_train, y_train)
    val_results["hist_gradient_boosting"] = compute_metrics(y_val, hgbr.predict(X_val))

    print("[train] Validation comparison:")
    for name, metrics in val_results.items():
        print(f"  - {name:28s}: MAE={metrics['mae']:.4f}, RMSE={metrics['rmse']:.4f}, WAPE={metrics['wape']:.4f}, Bias={metrics['bias']:+.4f}")

    # Select best ML model by validation WAPE, refit on train+val for final production model
    best_name = "hist_gradient_boosting" if val_results["hist_gradient_boosting"]["wape"] <= val_results["ridge_regression"]["wape"] else "ridge_regression"
    train_val_df = pd.concat([train_df, val_df], ignore_index=True)
    X_train_val = train_val_df[FEATURE_COLUMNS]
    y_train_val = train_val_df["sales"].to_numpy(dtype=np.float64)

    if best_name == "hist_gradient_boosting":
        final_model = HistGradientBoostingRegressor(**hgbr_params)
        hyperparameters = hgbr_params
    else:
        final_model = Ridge(alpha=10.0, random_state=42)
        hyperparameters = {"alpha": 10.0}

    final_model.fit(X_train_val, y_train_val)

    # Evaluate on completely held-out 28-day test period
    test_preds = np.clip(final_model.predict(X_test), 0.0, None)
    test_metrics = compute_metrics(y_test, test_preds)
    test_baselines = {
        "baseline_ma7": compute_metrics(y_test, test_df["rmean_7"].to_numpy()),
        "baseline_ma28": compute_metrics(y_test, test_df["rmean_28"].to_numpy()),
        "baseline_seasonal_naive7": compute_metrics(y_test, test_df["lag_7"].to_numpy()),
    }

    print(f"[train] Selected model: {best_name} ({MODEL_VERSION})")
    print(f"[train] Held-out Test metrics: {test_metrics}")

    model_path = artifacts_dir / "model.joblib"
    joblib.dump(final_model, model_path)

    metadata = {
        "model_version": MODEL_VERSION,
        "selected_model": best_name,
        "dataset": "Walmart M5 Forecasting Accuracy (sales_train_validation, calendar, sell_prices)",
        "target_variable": "daily_unit_sales",
        "forecast_horizon_days": 28,
        "trained_at": datetime.now(timezone.utc).isoformat(),
        "features": FEATURE_COLUMNS,
        "hyperparameters": hyperparameters,
        "split_info": preprocessor["split_info"],
        "validation_comparison": val_results,
        "test_metrics": test_metrics,
        "test_baselines": test_baselines,
        "limitations": [
            "M5 contains historical unit sales rather than unconstrained demand with verified stockout labels.",
            "Training uses a deterministic stratified subset of M5 store-SKU series across all 10 stores and 3 categories.",
            "Point forecasts are returned without fabricated confidence intervals.",
        ],
    }
    (artifacts_dir / "model_metadata.json").write_text(json.dumps(metadata, indent=2), encoding="utf-8")
    print(f"[train] Saved model -> {model_path} ({model_path.stat().st_size / 1024:.1f} KB)")
    return metadata


def main() -> None:
    parser = argparse.ArgumentParser(description="Train ShelfSense demand forecasting model.")
    parser.add_argument("--processed", type=Path, default=PROCESSED_DIR / "m5_features.pkl")
    args = parser.parse_args()
    train_and_select(processed_path=args.processed)


if __name__ == "__main__":
    main()
