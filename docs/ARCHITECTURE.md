# ShelfSense System Architecture

## Overview

ShelfSense is a Retail Inventory Intelligence and Demand Forecasting Platform composed of four integrated services:

```
┌───────────────────────────┐
│   React + TypeScript UI   │  (Vite / Tailwind / Recharts / Axios)
│   Nginx reverse proxy /api│
└─────────────┬─────────────┘
              │ HTTPS / JSON + JWT Bearer
              ▼
┌──────────────────────────────────────────────────────────┐
│           Spring Boot 4.1.1 Backend (Java 21)            │
│  ┌───────────┐ ┌───────────┐ ┌───────────┐ ┌──────────┐ │
│  │ Auth/RBAC │ │ Inventory │ │ Transfers │ │ Procure- │ │
│  │  Accounts │ │  & Ledger │ │  Workflow │ │   ment   │ │
│  └───────────┘ └───────────┘ └───────────┘ └──────────┘ │
│  ┌──────────────┐ ┌────────────────┐ ┌────────────────┐ │
│  │ Reservations │ │ Replenishment  │ │ ForecastClient │ │
│  │  & Expiry    │ │    Engine      │ │  (RestClient)  │ │
│  └──────────────┘ └────────────────┘ └───────┬────────┘ │
└───────────────────────┬──────────────────────┼──────────┘
                        │ JDBC + Flyway (V1-V11)│ HTTP JSON (timeouts + fallback)
                        ▼                       ▼
              ┌──────────────────┐    ┌───────────────────────┐
              │  PostgreSQL 16   │    │ FastAPI ML Service    │
              │  Persistent Vol  │    │ (Python + Scikit-Learn│
              │  Strict Checks   │    │  Walmart M5 Model)    │
              └──────────────────┘    └───────────────────────┘
```

## Key Architectural Decisions

1. **Transactional Inventory Invariants & Immutable Ledger**:
   - `inventory` tracks `on_hand`, `reserved`, and optimistic `@Version`, while `available = on_hand - reserved` is derived rather than stored.
   - Database `CHECK` constraints enforce `on_hand >= 0`, `reserved >= 0`, and `reserved <= on_hand`.
   - Every stock mutation (`INITIAL_STOCK`, `ADJUSTMENT`, `RESERVATION_*`, `TRANSFER_OUT`, `TRANSFER_IN`, `PURCHASE_RECEIVED`) atomically appends an immutable row to `inventory_transactions` inside the same database transaction.
2. **Concurrency & Idempotency**:
   - Reservations, transfer transitions, and PO goods receipts use `PESSIMISTIC_WRITE` row locks (`SELECT ... FOR UPDATE`) on aggregate state transitions alongside `@Version` optimistic locking on `inventory`.
   - Stock adjustments and goods receipts accept optional `Idempotency-Key` headers / payload keys backed by unique database constraints so retries never double-mutate stock.
3. **Security & Role-Based Access Control**:
   - Stateless JWT authentication (HS256, issuer & expiry validation) with BCrypt password hashing.
   - `EnabledAccountFilter` validates account active status on every request so disabled accounts cannot reuse previously issued JWTs.
   - `ADMIN` role gates catalog writes, stock adjustments, transfers, procurement, replenishment actions, and account management. `USER` accounts can browse operational views and manage only their own reservations (`account_id` ownership check).
4. **Resilient ML Service Decoupling**:
   - Spring Boot communicates with the Python FastAPI service via `RestClient` with configurable connect/read timeouts (`shelfsense.ml.*`).
   - ML outages return clean `503 SERVICE_UNAVAILABLE` responses and never block or corrupt inventory transactions; persisted `demand_forecasts` feed directly into `ReplenishmentService`.
