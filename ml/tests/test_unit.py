"""Fast, isolated unit tests for ShelfSense ML modules using synthetic fixtures.

These tests execute in seconds without loading 200MB+ dataset files or retraining models.
"""

from __future__ import annotations

import numpy as np
import pytest
from fastapi import HTTPException
from pydantic import ValidationError

from src.api.main import (
    BatchForecastRequest,
    ForecastRequest,
    ModelRegistry,
    forecast,
    forecast_batch,
    health,
    model_info,
)
from src.training.train import compute_metrics


class DummyModel:
    """Lightweight dummy estimator for unit testing inference logic without joblib loading."""

    def __init__(self, constant_pred: float = 3.5) -> None:
        self.constant_pred = constant_pred

    def predict(self, X) -> np.ndarray:
        return np.full(len(X), self.constant_pred)


@pytest.fixture
def mock_registry(monkeypatch):
    """Fixture that configures an in-memory mock ModelRegistry for fast endpoint testing."""
    import src.api.main as main_mod

    reg = ModelRegistry(main_mod.ARTIFACTS_DIR)
    reg.model = DummyModel(constant_pred=4.0)
    reg.preprocessor = {
        "feature_columns": [
            "lag_1", "lag_7", "lag_14", "lag_28",
            "rmean_7", "rmean_14", "rmean_28",
            "rstd_7", "rstd_28", "wday", "month",
            "day_of_month", "is_weekend", "has_event",
            "snap_active", "sell_price", "price_rel_mean",
            "cat_code", "store_code", "state_code",
        ],
        "cat_map": {"FOODS": 0, "HOBBIES": 1, "HOUSEHOLD": 2},
        "store_map": {"CA_1": 0, "TX_1": 1, "WI_1": 2},
        "state_map": {"CA": 0, "TX": 1, "WI": 2},
        "default_cat_code": 0,
        "default_store_code": 0,
        "default_state_code": 0,
        "median_sell_price": 3.50,
    }
    reg.metadata = {
        "model_version": "shelfsense-m5-test-mock",
        "algorithm": "DummyEstimator",
        "test_metrics": {"mae": 0.5, "rmse": 0.8, "wape": 0.15, "bias": 0.0},
    }
    reg.evaluation_report = {"summary": "unit-test-report"}

    monkeypatch.setattr(main_mod, "registry", reg)
    return reg


@pytest.mark.unit
def test_compute_metrics_exact_values() -> None:
    """Verifies that MAE, RMSE, WAPE, and Bias formulas match analytical values."""
    actual = np.array([2.0, 4.0, 6.0])
    pred = np.array([3.0, 4.0, 5.0])

    metrics = compute_metrics(actual, pred)
    assert metrics["mae"] == round(2.0 / 3.0, 4)
    assert metrics["rmse"] == round(np.sqrt(2.0 / 3.0), 4)
    assert metrics["wape"] == round(2.0 / 12.0, 4)
    assert metrics["bias"] == 0.0


@pytest.mark.unit
def test_compute_metrics_perfect_prediction() -> None:
    """Zero errors must yield exactly 0.0 for all distance metrics."""
    actual = np.array([10.0, 20.0, 30.0])
    pred = np.array([10.0, 20.0, 30.0])

    metrics = compute_metrics(actual, pred)
    assert metrics["mae"] == 0.0
    assert metrics["rmse"] == 0.0
    assert metrics["wape"] == 0.0
    assert metrics["bias"] == 0.0


@pytest.mark.unit
def test_pydantic_forecast_request_valid() -> None:
    """ForecastRequest accepts valid operational parameters."""
    req = ForecastRequest(
        product_id="SKU-100",
        store_id="STORE-1",
        category="FOODS",
        horizon_days=14,
        sell_price=4.99,
        recent_daily_sales=[3.0, 4.0, 2.0, 5.0],
    )
    assert req.product_id == "SKU-100"
    assert req.horizon_days == 14
    assert req.sell_price == 4.99
    assert len(req.recent_daily_sales) == 4


@pytest.mark.unit
def test_pydantic_forecast_request_validation_errors() -> None:
    """Rejects negative sales, invalid horizons, and empty IDs."""
    # Negative daily sales
    with pytest.raises(ValidationError):
        ForecastRequest(
            product_id="SKU-1",
            store_id="STORE-1",
            recent_daily_sales=[2.0, -1.0, 3.0],
        )

    # Zero or negative horizon
    with pytest.raises(ValidationError):
        ForecastRequest(product_id="SKU-1", store_id="STORE-1", horizon_days=0)

    # Excessive horizon (> 90 days)
    with pytest.raises(ValidationError):
        ForecastRequest(product_id="SKU-1", store_id="STORE-1", horizon_days=91)

    # Empty product ID
    with pytest.raises(ValidationError):
        ForecastRequest(product_id="", store_id="STORE-1")


@pytest.mark.unit
def test_fastapi_health_endpoint(mock_registry) -> None:
    """Health endpoint reports healthy status and loaded model."""
    res = health()
    assert res["status"] == "ok"
    assert res["model_loaded"] is True
    assert res["model_version"] == "shelfsense-m5-test-mock"


@pytest.mark.unit
def test_fastapi_model_info_endpoint(mock_registry) -> None:
    """Model info endpoint surfaces version, metadata, and evaluation summary."""
    info = model_info()
    assert info["status"] == "READY"
    assert info["model_version"] == "shelfsense-m5-test-mock"
    assert "metadata" in info
    assert "evaluation" in info


@pytest.mark.unit
def test_fastapi_forecast_single_prediction(mock_registry) -> None:
    """Single forecast returns exact daily projections matching requested horizon."""
    req = ForecastRequest(
        product_id="FOODS_3_090",
        store_id="CA_1",
        category="FOODS",
        horizon_days=7,
        sell_price=3.48,
        recent_daily_sales=[4, 5, 4, 3, 5, 4, 5],
    )
    res = forecast(req)
    assert res.product_id == "FOODS_3_090"
    assert res.store_id == "CA_1"
    assert res.horizon_days == 7
    assert len(res.daily_predictions) == 7
    assert res.predicted_daily_demand == 4.0
    assert res.predicted_total_demand == 28.0
    assert res.daily_predictions[0].day_offset == 1
    assert res.daily_predictions[6].day_offset == 7


@pytest.mark.unit
def test_fastapi_forecast_batch(mock_registry) -> None:
    """Batch forecast handles multiple requests and preserves per-item predictions."""
    batch_req = BatchForecastRequest(
        requests=[
            ForecastRequest(product_id="SKU-A", store_id="CA_1", horizon_days=5),
            ForecastRequest(product_id="SKU-B", store_id="TX_1", horizon_days=10),
        ]
    )
    res = forecast_batch(batch_req)
    assert res.count == 2
    assert len(res.predictions) == 2
    assert res.predictions[0].product_id == "SKU-A"
    assert res.predictions[0].horizon_days == 5
    assert res.predictions[1].product_id == "SKU-B"
    assert res.predictions[1].horizon_days == 10


@pytest.mark.unit
def test_fastapi_unloaded_model_raises_503(monkeypatch) -> None:
    """When model artifacts are missing, endpoints return clean 503 Service Unavailable."""
    import src.api.main as main_mod

    unloaded = ModelRegistry(main_mod.ARTIFACTS_DIR)
    unloaded.model = None
    unloaded.preprocessor = None
    monkeypatch.setattr(main_mod, "registry", unloaded)

    h = health()
    assert h["status"] == "degraded"
    assert h["model_loaded"] is False

    with pytest.raises(HTTPException) as exc_info:
        model_info()
    assert exc_info.value.status_code == 503

    req = ForecastRequest(product_id="SKU-1", store_id="STORE-1", horizon_days=7)
    with pytest.raises(HTTPException) as exc_info:
        forecast(req)
    assert exc_info.value.status_code == 503
