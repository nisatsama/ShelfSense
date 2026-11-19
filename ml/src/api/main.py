"""ShelfSense Demand Forecasting FastAPI Microservice."""

from __future__ import annotations

import json
import os
from datetime import date, datetime, timedelta, timezone
from pathlib import Path
from typing import Any, Dict, List, Optional

import joblib
import numpy as np
import pandas as pd
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field, field_validator

ML_ROOT = Path(__file__).resolve().parents[2]
ARTIFACTS_DIR = Path(os.environ.get("SHELFSENSE_ML_ARTIFACTS_DIR", str(ML_ROOT / "artifacts")))


class ForecastRequest(BaseModel):
    product_id: str = Field(..., min_length=1, max_length=120)
    store_id: str = Field(..., min_length=1, max_length=120)
    category: Optional[str] = Field(default="FOODS", max_length=100)
    horizon_days: int = Field(default=14, ge=1, le=90)
    sell_price: Optional[float] = Field(default=None, ge=0.0)
    recent_daily_sales: List[float] = Field(default_factory=list, max_length=365)
    start_date: Optional[date] = None

    @field_validator("recent_daily_sales")
    @classmethod
    def validate_non_negative_sales(cls, values: List[float]) -> List[float]:
        for v in values:
            if not np.isfinite(v) or v < 0:
                raise ValueError("recent_daily_sales must contain finite non-negative numbers")
        return values


class DailyPrediction(BaseModel):
    day_offset: int
    date: str
    predicted_demand: float


class ForecastResponse(BaseModel):
    product_id: str
    store_id: str
    horizon_days: int
    predicted_daily_demand: float
    predicted_total_demand: float
    model_version: str
    prediction_timestamp: str
    daily_predictions: List[DailyPrediction]


class BatchForecastRequest(BaseModel):
    requests: List[ForecastRequest] = Field(..., min_length=1, max_length=100)


class BatchForecastResponse(BaseModel):
    count: int
    model_version: str
    predictions: List[ForecastResponse]


class ModelRegistry:
    def __init__(self, artifacts_dir: Path) -> None:
        self.artifacts_dir = artifacts_dir
        self.model = None
        self.preprocessor: Optional[Dict[str, Any]] = None
        self.metadata: Dict[str, Any] = {}
        self.evaluation_report: Dict[str, Any] = {}

    def load(self) -> None:
        model_path = self.artifacts_dir / "model.joblib"
        preproc_path = self.artifacts_dir / "preprocessor.joblib"
        meta_path = self.artifacts_dir / "model_metadata.json"
        eval_path = self.artifacts_dir / "evaluation_report.json"

        if model_path.exists() and preproc_path.exists():
            self.model = joblib.load(model_path)
            self.preprocessor = joblib.load(preproc_path)
        if meta_path.exists():
            self.metadata = json.loads(meta_path.read_text(encoding="utf-8"))
        if eval_path.exists():
            self.evaluation_report = json.loads(eval_path.read_text(encoding="utf-8"))

    @property
    def is_ready(self) -> bool:
        return self.model is not None and self.preprocessor is not None

    @property
    def model_version(self) -> str:
        return str(self.metadata.get("model_version", "untrained"))


registry = ModelRegistry(ARTIFACTS_DIR)
registry.load()

app = FastAPI(
    title="ShelfSense Demand Forecasting Service",
    version="1.0.0",
    description="Retail demand forecasting microservice trained on Walmart M5 historical sales.",
)


def _encode_category(raw: Optional[str], cat_map: Dict[str, int], default_code: int) -> int:
    if not raw:
        return default_code
    upper = raw.strip().upper()
    if upper in cat_map:
        return cat_map[upper]
    # Heuristic mapping for domain categories outside raw M5 3-group names
    if any(k in upper for k in ("FOOD", "BEV", "DAIRY", "GROC", "COFFEE", "SNACK", "DRINK")):
        return cat_map.get("FOODS", default_code)
    if any(k in upper for k in ("HOBB", "TOY", "SPORT", "BOOK", "GAME", "ELECTR")):
        return cat_map.get("HOBBIES", default_code)
    return cat_map.get("HOUSEHOLD", default_code)


def _m5_wday(dt: date) -> int:
    # M5 convention: Saturday=1, Sunday=2, Monday=3, ..., Friday=7
    return ((dt.weekday() + 2) % 7) + 1


def _predict_single(req: ForecastRequest) -> ForecastResponse:
    if not registry.is_ready:
        raise HTTPException(
            status_code=503,
            detail="Forecasting model artifacts are not loaded. Run the training pipeline first.",
        )

    pre = registry.preprocessor
    assert pre is not None
    feature_cols: List[str] = pre["feature_columns"]
    cat_code = _encode_category(req.category, pre["cat_map"], pre["default_cat_code"])
    store_key = req.store_id.strip().upper()
    store_code = pre["store_map"].get(store_key, pre["default_store_code"])
    state_prefix = store_key.split("_")[0] if "_" in store_key else ""
    state_code = pre["state_map"].get(state_prefix, pre["default_state_code"])

    median_price = float(pre.get("median_sell_price", 5.0))
    sell_price = float(req.sell_price) if (req.sell_price is not None and req.sell_price > 0) else median_price
    price_rel_mean = float(np.clip(sell_price / max(median_price, 0.01), 0.25, 4.0))

    # Build a 28-day rolling history buffer from supplied history (or conservative default if empty)
    if req.recent_daily_sales:
        base_series = [float(x) for x in req.recent_daily_sales]
        pad_value = float(np.mean(base_series[-7:])) if base_series else 1.0
        history = ([pad_value] * max(0, 28 - len(base_series))) + base_series[-28:]
    else:
        history = [1.0] * 28

    start = req.start_date or (datetime.now(timezone.utc).date() + timedelta(days=1))
    daily_points: List[DailyPrediction] = []

    for offset in range(req.horizon_days):
        target_date = start + timedelta(days=offset)
        wday = _m5_wday(target_date)
        row = {
            "lag_1": history[-1],
            "lag_7": history[-7],
            "lag_14": history[-14],
            "lag_28": history[-28],
            "rmean_7": float(np.mean(history[-7:])),
            "rmean_14": float(np.mean(history[-14:])),
            "rmean_28": float(np.mean(history[-28:])),
            "rstd_7": float(np.std(history[-7:], ddof=1)) if len(history[-7:]) > 1 else 0.0,
            "rstd_28": float(np.std(history[-28:], ddof=1)) if len(history[-28:]) > 1 else 0.0,
            "wday": wday,
            "month": target_date.month,
            "day_of_month": target_date.day,
            "is_weekend": 1 if wday in (1, 2) else 0,
            "has_event": 0,
            "snap_active": 1 if target_date.day <= 10 else 0,
            "sell_price": sell_price,
            "price_rel_mean": price_rel_mean,
            "cat_code": cat_code,
            "store_code": store_code,
            "state_code": state_code,
        }
        X_step = pd.DataFrame([[row[c] for c in feature_cols]], columns=feature_cols)
        pred = float(np.clip(registry.model.predict(X_step)[0], 0.0, None))
        history.append(pred)
        daily_points.append(
            DailyPrediction(
                day_offset=offset + 1,
                date=target_date.isoformat(),
                predicted_demand=round(pred, 4),
            )
        )

    total_demand = sum(p.predicted_demand for p in daily_points)
    avg_daily = total_demand / len(daily_points)

    return ForecastResponse(
        product_id=req.product_id,
        store_id=req.store_id,
        horizon_days=req.horizon_days,
        predicted_daily_demand=round(avg_daily, 4),
        predicted_total_demand=round(total_demand, 4),
        model_version=registry.model_version,
        prediction_timestamp=datetime.now(timezone.utc).isoformat(),
        daily_predictions=daily_points,
    )


@app.get("/health")
def health() -> Dict[str, Any]:
    return {
        "status": "ok" if registry.is_ready else "degraded",
        "model_loaded": registry.is_ready,
        "model_version": registry.model_version,
    }


@app.get("/model/info")
def model_info() -> Dict[str, Any]:
    if not registry.is_ready:
        raise HTTPException(status_code=503, detail="Model artifacts not loaded.")
    return {
        "status": "READY",
        "model_version": registry.model_version,
        "metadata": registry.metadata,
        "evaluation": registry.evaluation_report,
    }


@app.post("/api/v1/forecast", response_model=ForecastResponse)
def forecast(request: ForecastRequest) -> ForecastResponse:
    return _predict_single(request)


@app.post("/api/v1/forecast/batch", response_model=BatchForecastResponse)
def forecast_batch(batch: BatchForecastRequest) -> BatchForecastResponse:
    preds = [_predict_single(item) for item in batch.requests]
    return BatchForecastResponse(
        count=len(preds),
        model_version=registry.model_version,
        predictions=preds,
    )
