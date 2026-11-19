"""Integration tests for ShelfSense ML artifacts, real model inference, and dataset splits.

These tests run against saved disk artifacts (model.joblib, preprocessor.joblib, evaluation_report.json)
without triggering expensive full retraining or downloading raw datasets.
"""

from __future__ import annotations

import json
from pathlib import Path

import joblib
import pandas as pd
import pytest

from src.api.main import (
    BatchForecastRequest,
    ForecastRequest,
    forecast,
    forecast_batch,
    health,
    model_info,
    registry,
)
from src.data.preprocess import ARTIFACTS_DIR, FEATURE_COLUMNS, PROCESSED_DIR


@pytest.mark.integration
def test_saved_artifacts_exist_and_load() -> None:
    """Verifies all required production model artifacts exist on disk and load correctly."""
    model_path = ARTIFACTS_DIR / "model.joblib"
    pre_path = ARTIFACTS_DIR / "preprocessor.joblib"
    meta_path = ARTIFACTS_DIR / "model_metadata.json"
    eval_path = ARTIFACTS_DIR / "evaluation_report.json"

    assert model_path.exists(), f"Missing model artifact: {model_path}"
    assert pre_path.exists(), f"Missing preprocessor artifact: {pre_path}"
    assert meta_path.exists(), f"Missing metadata artifact: {meta_path}"
    assert eval_path.exists(), f"Missing evaluation report: {eval_path}"

    model = joblib.load(model_path)
    assert hasattr(model, "predict"), "Trained model must expose a .predict() method"

    pre = joblib.load(pre_path)
    assert "feature_columns" in pre
    assert "cat_map" in pre
    assert "store_map" in pre
    assert "state_map" in pre

    meta = json.loads(meta_path.read_text(encoding="utf-8"))
    assert meta["model_version"].startswith("shelfsense-m5")
    assert meta["test_metrics"]["mae"] > 0
    assert meta["test_metrics"]["rmse"] > 0
    assert meta["test_metrics"]["wape"] > 0

    report = json.loads(eval_path.read_text(encoding="utf-8"))
    assert len(report["daily_actual_vs_predicted"]) == 28


@pytest.mark.integration
def test_end_to_end_real_model_inference() -> None:
    """Performs live inference using the actual trained HistGradientBoosting model."""
    registry.load()
    assert registry.is_ready is True

    # Health & Info
    h = health()
    assert h["status"] == "ok"
    assert h["model_loaded"] is True

    info = model_info()
    assert info["status"] == "READY"

    # Single Forecast
    req = ForecastRequest(
        product_id="FOODS_3_090",
        store_id="CA_1",
        category="FOODS",
        horizon_days=14,
        sell_price=3.48,
        recent_daily_sales=[4, 5, 3, 6, 7, 5, 4, 3, 5, 6, 4, 5, 6, 7],
    )
    res = forecast(req)
    assert res.product_id == "FOODS_3_090"
    assert res.horizon_days == 14
    assert len(res.daily_predictions) == 14
    assert res.predicted_daily_demand >= 0.0
    assert res.predicted_total_demand >= 0.0

    # Batch Forecast
    batch = BatchForecastRequest(
        requests=[
            ForecastRequest(product_id="SKU-1", store_id="TX_1", horizon_days=7, recent_daily_sales=[2, 3, 2, 4]),
            ForecastRequest(product_id="SKU-2", store_id="WI_2", horizon_days=7, recent_daily_sales=[1, 0, 2, 1]),
        ]
    )
    batch_res = forecast_batch(batch)
    assert batch_res.count == 2
    assert len(batch_res.predictions) == 2


@pytest.mark.integration
def test_processed_features_temporal_integrity() -> None:
    """Verifies that the preprocessed feature dataset has zero future leakage."""
    features_path = PROCESSED_DIR / "m5_features.pkl"
    if not features_path.exists():
        pytest.skip("m5_features.pkl not found; skipping feature dataset check.")

    df = pd.read_pickle(features_path)
    assert set(FEATURE_COLUMNS).issubset(df.columns)

    train_max = df.loc[df["split"] == "train", "day_num"].max()
    val_min = df.loc[df["split"] == "val", "day_num"].min()
    val_max = df.loc[df["split"] == "val", "day_num"].max()
    test_min = df.loc[df["split"] == "test", "day_num"].min()

    assert train_max < val_min, "Train period must strictly precede validation period."
    assert val_max < test_min, "Validation period must strictly precede test period."
    assert not df[FEATURE_COLUMNS].isna().any().any(), "Feature columns must not contain NaNs."
