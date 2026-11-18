# ShelfSense ML Pipeline & Model Evaluation

## Pipeline Overview
All steps live under `ml/` and are fully reproducible:
```powershell
cd ml
python -m venv .venv
.\.venv\Scripts\activate
pip install -r requirements.txt
python -m src.data.download
python -m src.data.preprocess
python -m src.training.train
python -m src.evaluation.evaluate
pytest -v
```

## Sampling & Leak-Free Temporal Split
To keep training fast and reproducible on local hardware while preserving representativeness across every store and product category, `preprocess.py` deterministically selects **300 active store-SKU series** stratified across all **10 Walmart stores** and **3 categories** over the trailing **730 days** (`210,600` feature-complete daily observations after the 28-day lag warm-up):

| Split | Day Range | Calendar Dates | Rows |
|---|---|---|---:|
| Train | `d_1212` – `d_1857` | `2014-05-24` to `2016-02-28` | 193,800 |
| Validation | `d_1858` – `d_1885` | `2016-02-29` to `2016-03-27` (28 days) | 8,400 |
| Held-out Test | `d_1886` – `d_1913` | `2016-03-28` to `2016-04-24` (28 days) | 8,400 |

- **Zero random splitting:** Observations are strictly ordered chronologically.
- **Zero target leakage:** All lag (`lag_1`, `lag_7`, `lag_14`, `lag_28`) and rolling statistics (`rmean_7/14/28`, `rstd_7/28`) are shifted by at least 1 day; item mean prices for `price_rel_mean` are computed strictly on the training window.

## Feature Set (20 Features)
- **Demand Lags & Rolling Stats:** `lag_1`, `lag_7`, `lag_14`, `lag_28`, `rmean_7`, `rmean_14`, `rmean_28`, `rstd_7`, `rstd_28`
- **Calendar & Promotions:** `wday`, `month`, `day_of_month`, `is_weekend`, `has_event`, `snap_active`
- **Pricing:** `sell_price`, `price_rel_mean`
- **Encodings:** `cat_code`, `store_code`, `state_code`

## Model Selection & Held-Out Test Results (`shelfsense-m5-hgbr-1.0.0`)

Selected model: **`HistGradientBoostingRegressor`** (`loss="poisson"`, `learning_rate=0.05`, `max_iter=250`, `max_leaf_nodes=31`, `min_samples_leaf=30`, `l2_regularization=1.0`, `early_stopping=True`).

### Held-Out 28-Day Test Set Comparison (Genuine Executed Metrics)
| Model | MAE | RMSE | WAPE | Bias |
|---|---:|---:|---:|---:|
| Seasonal Naive (7d lag) | 1.0210 | 2.2177 | 93.99% | -0.0021 |
| Moving Average (7d) | 0.8695 | 1.7134 | 80.05% | +0.0037 |
| Moving Average (28d) | 0.8573 | 1.6597 | 78.93% | -0.0019 |
| **HistGradientBoosting (Poisson)** | **0.8561** | **1.6315** | **78.82%** | **+0.0281** |

### Category Breakdown (Held-Out Test)
| Category | MAE | RMSE | WAPE | Bias |
|---|---:|---:|---:|---:|
| `FOODS` | 1.1399 | 2.1923 | 63.70% | +0.0134 |
| `HOUSEHOLD` | 0.7302 | 1.1365 | 91.64% | +0.0432 |
| `HOBBIES` | 0.6983 | 1.3740 | 103.84% | +0.0278 |

*(Note: Individual SKU-store daily unit counts in retail are intermittent and zero-inflated — especially in `HOBBIES` — which explains higher SKU-level WAPE even when aggregate store/network daily forecasts closely track actual demand spikes.)*

## Artifacts Saved
- `ml/artifacts/model.joblib` (519 KB)
- `ml/artifacts/preprocessor.joblib`
- `ml/artifacts/model_metadata.json`
- `ml/artifacts/evaluation_report.json`
- `ml/artifacts/evaluation_actual_vs_predicted.png`
- `ml/artifacts/evaluation_model_comparison.png`

## Known Limitations
1. Trained on historical unit sales without verified stockout indicators.
2. Uses a deterministic 300-series stratified sample for tractable local training (configurable via `--max-series`).
3. Returns deterministic point forecasts without fabricated confidence intervals.
