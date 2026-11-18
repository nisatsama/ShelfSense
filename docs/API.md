# ShelfSense API Reference

Base URL: `/api/v1`  
Authentication: `Authorization: Bearer <jwt>` (except `/auth/login`, `/auth/register`, and `/actuator/health`).

## Authentication & Accounts
| Method | Endpoint | Role | Description |
|---|---|---|---|
| `POST` | `/api/v1/auth/login` | Public | Authenticate with `loginIdentifier` & `password`; returns Bearer JWT |
| `POST` | `/api/v1/auth/register` | Public | Self-register a `USER` account |
| `GET` | `/api/v1/auth/me` | Authenticated | Current authenticated account profile |
| `GET` | `/api/v1/accounts` | `ADMIN` | List all accounts (without password hashes) |
| `POST` | `/api/v1/accounts` | `ADMIN` | Create account with explicit role (`USER` or `ADMIN`) |
| `PATCH` | `/api/v1/accounts/{id}/status` | `ADMIN` | Enable or disable an account |
| `PATCH` | `/api/v1/accounts/{id}/role` | `ADMIN` | Change account role |

## Products & Locations
| Method | Endpoint | Role | Description |
|---|---|---|---|
| `GET` | `/api/v1/products?q=&category=&active=` | Authenticated | List / search products |
| `GET` | `/api/v1/products/{id}` | Authenticated | Get product details |
| `POST` | `/api/v1/products` | `ADMIN` | Create product |
| `PUT` | `/api/v1/products/{id}` | `ADMIN` | Update product |
| `GET` | `/api/v1/locations?type=&q=` | Authenticated | List / filter locations (`WAREHOUSE`, `DISTRIBUTION_CENTER`, `STORE`) |
| `GET` | `/api/v1/locations/{id}` | Authenticated | Get location details |
| `POST` | `/api/v1/locations` | `ADMIN` | Create location |
| `PUT` | `/api/v1/locations/{id}` | `ADMIN` | Update location |

## Inventory, Adjustments & Ledger
| Method | Endpoint | Role | Description |
|---|---|---|---|
| `GET` | `/api/v1/inventory?productId=&locationId=&lowStockThreshold=` | Authenticated | List stock levels |
| `GET` | `/api/v1/inventory/products/{productId}/locations/{locationId}` | Authenticated | Lookup specific stock row |
| `POST` | `/api/v1/inventory` | `ADMIN` | Initialize stock (`INITIAL_STOCK` ledger entry) |
| `POST` | `/api/v1/inventory/adjustments` | `ADMIN` | Idempotent stock adjustment (`Idempotency-Key` supported) |
| `GET` | `/api/v1/inventory/adjustments?inventoryId=` | Authenticated | Audit list of stock adjustments |
| `GET` | `/api/v1/inventory/transactions?inventoryId=&type=&limit=` | Authenticated | Query immutable inventory ledger |

## Reservations
| Method | Endpoint | Role | Description |
|---|---|---|---|
| `POST` | `/api/v1/reservations` | Authenticated | Reserve available stock (10-min expiry, bound to account) |
| `GET` | `/api/v1/reservations?status=` | Authenticated | Own reservations (`USER`) or all (`ADMIN`) |
| `GET` | `/api/v1/reservations/{id}` | Owner / `ADMIN` | Get reservation |
| `PUT` | `/api/v1/reservations/{id}/confirm` | Owner / `ADMIN` | Confirm hold and decrement `onHand` + `reserved` |
| `PUT` | `/api/v1/reservations/{id}/release` | Owner / `ADMIN` | Release hold and restore `available` |

## Multi-Location Transfers
| Method | Endpoint | Role | Description |
|---|---|---|---|
| `POST` | `/api/v1/transfers` | `ADMIN` | Create transfer (`CREATED`) |
| `GET` | `/api/v1/transfers?status=&locationId=` | Authenticated | List transfers |
| `PUT` | `/api/v1/transfers/{id}/approve` | `ADMIN` | `CREATED -> APPROVED` |
| `PUT` | `/api/v1/transfers/{id}/dispatch` | `ADMIN` | `APPROVED -> DISPATCHED` (deducts source stock + `TRANSFER_OUT`) |
| `PUT` | `/api/v1/transfers/{id}/in-transit` | `ADMIN` | `DISPATCHED -> IN_TRANSIT` |
| `PUT` | `/api/v1/transfers/{id}/receive` | `ADMIN` | Partial/full receipt + damaged qty (adds good stock + `TRANSFER_IN`) |
| `PUT` | `/api/v1/transfers/{id}/cancel` | `ADMIN` | Cancel `CREATED` or `APPROVED` transfer |

## Procurement (Suppliers, Purchase Orders, Goods Receipts)
| Method | Endpoint | Role | Description |
|---|---|---|---|
| `GET` / `POST` / `PUT` | `/api/v1/suppliers`, `/api/v1/suppliers/{id}` | Read: Auth / Write: `ADMIN` | Manage suppliers & lead times |
| `GET` / `POST` | `/api/v1/purchase-orders` | Read: Auth / Write: `ADMIN` | List and create purchase orders |
| `PUT` | `/api/v1/purchase-orders/{id}/{approve\|order\|cancel}` | `ADMIN` | Transition PO status |
| `POST` | `/api/v1/purchase-orders/{id}/receipts` | `ADMIN` | Idempotent partial/full goods receipt (`PURCHASE_RECEIVED`) |
| `GET` | `/api/v1/purchase-orders/{id}/receipts` | Authenticated | List goods receipts for PO |

## Demand Forecasting, Replenishment & Dashboard
| Method | Endpoint | Role | Description |
|---|---|---|---|
| `GET` | `/api/v1/forecasts?productId=&locationId=` | Authenticated | List persisted forecasts |
| `GET` | `/api/v1/forecasts/model-info` | Authenticated | Proxy live FastAPI model metadata & evaluation metrics |
| `POST` | `/api/v1/forecasts/generate` | Authenticated | Request forecast from FastAPI ML service and persist |
| `GET` | `/api/v1/replenishment?status=&priority=&locationId=` | Authenticated | List replenishment recommendations |
| `POST` | `/api/v1/replenishment/generate` | `ADMIN` | Run ROP / safety stock calculation (deduplicated) |
| `PUT` | `/api/v1/replenishment/{id}/{approve\|dismiss}` | `ADMIN` | Approve (optionally auto-creating a PO) or dismiss |
| `GET` | `/api/v1/dashboard/summary` | Authenticated | Aggregated KPI counts for operations dashboard |

## Python ML Service Contract (Internal)
- `GET /health` -> `{status, model_loaded, model_version}`
- `GET /model/info` -> `{status, model_version, metadata, evaluation}`
- `POST /api/v1/forecast` -> request `{product_id, store_id, category, horizon_days, sell_price, recent_daily_sales}` -> response `{product_id, store_id, horizon_days, predicted_daily_demand, predicted_total_demand, model_version, prediction_timestamp, daily_predictions[]}`
- `POST /api/v1/forecast/batch` -> `{requests: [...]}` -> `{count, model_version, predictions: [...]}`
