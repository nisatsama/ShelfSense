# ShelfSense Demand Forecasting Dataset

## Primary Dataset
- **Name:** M5 Forecasting — Accuracy (Walmart US Retail Sales)
- **Official Source:** [Kaggle M5 Forecasting Accuracy Competition](https://www.kaggle.com/competitions/m5-forecasting-accuracy/data)
- **Automated Programmatic Source:** `ml/src/data/download.py` checks for local Kaggle CLI credentials (`~/.kaggle/kaggle.json` or `KAGGLE_USERNAME`/`KAGGLE_KEY`) first, and falls back to the public Nixtla M5 dataset archive (`https://github.com/Nixtla/m5-forecasts/raw/main/datasets/m5.zip`) containing the original M5 CSV files.
- **License & Terms:** Subject to the Walmart M5 Forecasting competition rules and academic/non-commercial research terms published on Kaggle. Raw CSV files (`~340 MB`) are stored locally under `ml/data/raw/` and are strictly excluded from Git via `.gitignore`.

## Files & Schema
| File | Approximate Size | Contents |
|---|---:|---|
| `sales_train_validation.csv` | 113.9 MB | Daily historical unit sales across 30,490 store-SKU time series (`d_1`..`d_1913`), spanning 10 Walmart stores (`CA_1..CA_4`, `TX_1..TX_3`, `WI_1..WI_3`) and 3 categories (`FOODS`, `HOBBIES`, `HOUSEHOLD`). |
| `calendar.csv` | 0.11 MB | Date alignment, weekday (`wday`), month, year, special events/holidays (`event_name_1`, `event_type_1`), and state-level SNAP promotion flags (`snap_CA`, `snap_TX`, `snap_WI`). |
| `sell_prices.csv` | 226.1 MB | Weekly item-store selling prices keyed by `(store_id, item_id, wm_yr_wk)`. |

## Important Business Limitation
The Walmart M5 dataset provides **historical unit sales**, NOT historical on-hand inventory balances or verified stockout labels.
- Historical unit sales are treated as the supervised forecasting target (`daily_unit_sales`).
- ShelfSense does **not** claim historical sales equal unconstrained demand during unrecorded stockouts, nor does it fabricate stockout labels in real-world model evaluation.
- Inventory movements used to test backend replenishment workflows in JUnit integration tests are isolated transactional test fixtures and are never mixed into ML training or evaluation metrics.
