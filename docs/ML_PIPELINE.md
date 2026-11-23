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

---

## 1. Domain Demand Extraction Semantics

ShelfSense extracts historical demand directly from the transactional inventory ledger (`inventory_transactions`). To prevent domain distortions and demand contamination, transaction types are categorized strictly:

### Included Customer Demand Events
- **`SALE`**: Direct customer purchase decrementing on-hand stock (`on_hand_delta < 0, reserved_delta == 0`).
- **`RESERVATION_CONFIRMED`**: Customer order fulfillment where a customer reservation is confirmed, decrementing both on-hand and reserved quantities (`on_hand_delta < 0, reserved_delta < 0, on_hand_delta == reserved_delta`).
- **Double-Counting Prevention**: If a `SALE` entry references a reservation ID that was already processed as a `RESERVATION_CONFIRMED` transaction, it is deduplicated and not counted twice.

### Explicitly Excluded Non-Demand Movements
- **`TRANSFER_OUT` & `TRANSFER_IN`**: Inter-location inventory rebalancing between warehouses, distribution centers, and stores. Moving stock out of a central warehouse to a retail store does **not** represent customer consumption and must never be counted as customer demand.
- **`INITIAL_STOCK`**: Baseline inventory configuration upon item/location creation.
- **`PURCHASE_RECEIVED`**: Inbound supplier receipts from purchase orders.
- **`ADJUSTMENT`**: Stock corrections from shrinkage, damage, cycle counts, or audit write-offs.
- **`RESERVATION_CREATED`, `RESERVATION_RELEASED`, `RESERVATION_EXPIRED`**: Temporary customer hold lifecycle transitions before fulfillment.

---

## 2. Location Feature Strategy & Fallback Contract

The pre-trained demand model is fitted on Walmart M5 historical series representing 10 store locations across 3 states (`CA_1`–`CA_4`, `TX_1`–`TX_3`, `WI_1`–`WI_3`).

ShelfSense application locations (e.g., `WH-MAIN`, `STORE-01`) do not correspond directly to Walmart stores. To avoid silent misrepresentation or falsely attributing California-specific demographics to a regional store, an explicit resolution strategy is enforced:

1. **Recognized M5 Store Identifiers**: If `store_id` matches an M5 training store, the model uses learned store and state parameters.
2. **Unmapped Application Locations**: If `store_id` is an internal code (e.g. `STORE-01`), the inference engine:
   - Sets `location_mapped = false` and `store_resolution = "UNMAPPED_GENERIC_STORE"`.
   - Uses an explicit unmapped baseline store code.
   - Neutralizes state-specific SNAP policy (`snap_active = 0`), preventing false demand spikes based on California/Texas food-stamp calendars.
   - Returns transparency metadata in `ForecastResponse` so callers and operators clearly see whether exact store features were utilized.

---

## 3. Sampling & Leak-Free Temporal Split

`preprocess.py` deterministically samples **300 active store-SKU series** stratified across all **10 Walmart stores** and **3 categories** over the trailing **730 days** (`210,600` feature-complete daily observations after the 28-day lag warm-up):

| Split | Day Range | Calendar Dates | Rows |
|---|---|---|---:|
| Train | `d_1212` – `d_1857` | `2014-05-24` to `2016-02-28` | 193,800 |
| Validation | `d_1858` – `d_1885` | `2016-02-29` to `2016-03-27` (28 days) | 8,400 |
| Held-out Test | `d_1886` – `d_1913` | `2016-03-28` to `2016-04-24` (28 days) | 8,400 |

- **Zero random splitting:** Observations are strictly ordered chronologically.
- **Zero target leakage:** All lag (`lag_1`, `lag_7`, `lag_14`, `lag_28`) and rolling statistics (`rmean_7/14/28`, `rstd_7/28`) are shifted by at least 1 day; item mean prices for `price_rel_mean` are computed strictly on the training window.

---

## 4. Feature Set (20 Features)
- **Demand Lags & Rolling Stats:** `lag_1`, `lag_7`, `lag_14`, `lag_28`, `rmean_7`, `rmean_14`, `rmean_28`, `rstd_7`, `rstd_28`
- **Calendar & Promotions:** `wday`, `month`, `day_of_month`, `is_weekend`, `has_event`, `snap_active`
- **Pricing:** `sell_price`, `price_rel_mean`
- **Encodings:** `cat_code`, `store_code`, `state_code`

---

## 5. Model Selection & Recursive Multi-Step Evaluation

### Evaluation Protocol Alignment
Production inference generates multi-day forecasts by iteratively predicting day $t$ and appending that prediction to a rolling history buffer to compute dynamic lags for day $t+1$ (autoregressive recursive rollout).

In contrast to 1-step ahead evaluation with ground-truth lags, `evaluate.py` simulates the exact production recursive rollout across the held-out test set for **14-day** and **28-day** horizons:

### Held-Out Test Evaluation Metrics

| Horizon | Model / Baseline | MAE | RMSE | WAPE | Bias |
|---|---|---:|---:|---:|---:|
| **14-Day Horizon** | **Recursive HistGradientBoosting** | **0.9228** | **1.7151** | **82.12%** | **+0.1008** |
| 14-Day Horizon | Moving Average (7-day flat baseline) | 0.8872 | 1.7042 | 78.95% | +0.0210 |
| 14-Day Horizon | Moving Average (28-day flat baseline) | 0.8801 | 1.6961 | 78.32% | -0.0042 |
| 14-Day Horizon | Seasonal Naïve (7-day periodic baseline) | 1.0714 | 2.3789 | 95.34% | -0.0125 |
| **28-Day Horizon** | **Recursive HistGradientBoosting** | **0.9300** | **1.7515** | **85.62%** | **+0.1004** |
| 28-Day Horizon | Moving Average (7-day flat baseline) | 0.8954 | 1.7380 | 82.44% | +0.0150 |
| 28-Day Horizon | Moving Average (28-day flat baseline) | 0.8768 | 1.6988 | 80.73% | -0.0110 |
| 28-Day Horizon | Seasonal Naïve (7-day periodic baseline) | 1.0969 | 2.4054 | 100.99% | -0.0310 |
| *Reference (1-step)* | *HistGradientBoosting (Teacher-forced 1-day)* | *0.8561* | *1.6315* | *78.82%* | *+0.0281* |

### Honest Technical Analysis: Autoregressive Compounding Error
1. **Teacher-Forced vs. Recursive Rollout**: The 1-step ahead prediction achieves 78.82% WAPE. When rolled out recursively, prediction errors compound into subsequent lag features, resulting in 82.12% WAPE at 14 days and 85.62% WAPE at 28 days.
2. **Comparison with Baselines**:
   - The 28-day moving average baseline achieves 80.73% WAPE on aggregate test volume, demonstrating that static rolling baselines are competitive for long horizons when demand is intermittent.
   - However, the ML model dramatically outperforms the Seasonal Naïve baseline (85.62% vs 100.99% WAPE) by capturing calendar patterns, day-of-week demand surges, and price elasticities without collapsing into high-variance point oscillations.

---

## 6. Artifacts Saved
- `ml/artifacts/model.joblib`: Trained HistGradientBoosting model (Poisson loss).
- `ml/artifacts/preprocessor.joblib`: Feature schemas, categorical encoders, and store resolution mapping.
- `ml/artifacts/model_metadata.json`: Model version, training parameters, and dataset splits.
- `ml/artifacts/evaluation_report.json`: Multi-horizon recursive metrics and baseline comparisons.
- `ml/artifacts/evaluation_actual_vs_predicted.png`: Daily actual vs recursive predicted demand across test horizon.
- `ml/artifacts/evaluation_model_comparison.png`: 14d and 28d WAPE/MAE comparisons across all evaluated models.

---

## 7. Known Assumptions & Limitations
1. **Intermittent Sales Target**: Trained on historical unit sales without explicit stockout labels; unconstrained customer demand is inferred from realized sales and confirmed orders.
2. **Recursive Horizon Limits**: Forecast horizons beyond 28 days experience greater compounding uncertainty; 7–14 day horizons are recommended for replenishment decisions.
3. **Point Estimates**: Deterministic point forecasts are returned without fabricated statistical confidence intervals.
