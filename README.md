# ShelfSense

### Retail Inventory Intelligence & Replenishment Platform

**Predict stockouts. Optimize inventory. Make replenishment decisions before shelves go empty.**

ShelfSense is a full-stack retail inventory intelligence and demand forecasting platform combining a transactional **Java Spring Boot** backend, **PostgreSQL 16** with Flyway schema migrations, a **Python FastAPI** demand forecasting microservice trained on the real **Walmart M5** dataset, and a modern **React 19 + TypeScript** operations dashboard.

Traditional inventory systems primarily report how much stock is available. ShelfSense goes further: tracking inventory across Warehouses, Distribution Centres, and Retail Stores, predicting demand patterns, automating reorder point calculations, and coordinating multi-location stock movements before shelves run empty.

---

## Table of Contents

- [Problem Statement](#problem-statement)
- [Solution](#solution)
- [Key Capabilities](#key-capabilities)
- [System Architecture](#system-architecture)
- [Technology Stack](#technology-stack)
- [Core Domain Model](#core-domain-model)
- [Inventory Management & Immutable Ledger](#inventory-management--immutable-ledger)
- [Reservation Lifecycle](#reservation-lifecycle)
- [Demand Forecasting & Stockout Prediction](#demand-forecasting--stockout-prediction)
- [Replenishment Engine](#replenishment-engine)
- [API Design](#api-design)
- [Database Design & Migrations](#database-design--migrations)
- [Project Structure](#project-structure)
- [Getting Started](#getting-started)
  - [Option A: Full-Stack Docker Compose (Recommended)](#option-a-full-stack-docker-compose-recommended)
  - [Option B: Local Development](#option-b-local-development)
- [Running Tests](#running-tests)
- [Engineering Roadmap](#engineering-roadmap)
- [Design Decisions](#design-decisions)
- [Contributing](#contributing)
- [License](#license)
- [Author](#author)

---

## Problem Statement

Retail businesses operate across multiple inventory nodes:

- **Warehouses** hold bulk inventory and handle supplier receipts.
- **Distribution Centers** distribute stock across regional networks.
- **Retail Stores** maintain customer-facing inventory with high turnover.
- **Suppliers** replenish stock subject to variable lead times.

Managing these locations independently creates critical operational problems:

1. **Unexpected Stockouts:** Products appear sufficiently stocked today but run out before replenishment arrives due to unforecasted demand surges.
2. **Inventory Fragmentation:** Stock sits idle in one central warehouse while a retail store experiences localized stockouts.
3. **Inaccurate Available Inventory:** Concurrent customer orders and holds cause double-allocation without strict transactional locking.
4. **Reactive Replenishment:** Purchase orders are placed only after stock drops below arbitrary thresholds rather than lead-time demand projections.
5. **Limited Audit Visibility:** Discrepancies from shrinkage, damage, or cycle counts cannot be reconciled without an immutable ledger of adjustments.

ShelfSense addresses these problems through reliable transactional inventory operations, automated replenishment intelligence, and machine-learning-backed demand forecasts.

---

## Solution

ShelfSense introduces an intelligence and governance layer on top of conventional stock management.

Consider a retail store with the following shampoo inventory:

| Metric | Value |
|---|---:|
| Product | Daily Care Shampoo |
| Current on-hand stock | 120 units |
| Reserved stock (pending pickup) | 15 units |
| **Available stock** | **105 units** |
| Estimated demand (ML forecast) | 5.5 units / hour |
| Supplier lead time | 24 hours |

**Operational Calculation:**
- Available stock: 105 units
- Estimated time until stockout: $\frac{105}{5.5} \approx 19.1 \text{ hours}$
- Supplier delivery lead time: 24.0 hours

Because estimated stockout occurs in 19 hours but supplier lead time is 24 hours, waiting for regular purchasing guarantees a 5-hour stockout. ShelfSense detects this gap in advance, generating an urgent transfer recommendation from a regional warehouse or creating an expedited purchase order.

---

## Key Capabilities

### Implemented Platform Features

- **Authentication & RBAC:** Stateless JWT authentication (HS256, BCrypt, 1-hour expiry), user self-registration (`USER`), account status controls, and `ADMIN`-only administration.
- **Product & Location Network:** SKU management, category organization, unit pricing, and node management (Warehouse, Distribution Centre, Retail Store) with search and filtering.
- **Stock Tracking & Derived Available:** Strict inventory separation:
  $$\text{available} = \text{onHand} - \text{reserved}$$
  Enforced by database-level CHECK constraints (`on_hand >= 0`, `reserved >= 0`, `reserved <= on_hand`).
- **Immutable Transaction Ledger:** Every stock alteration records an append-only transaction entry (`inventory_transactions`) capturing deltas, balance snapshots, reference IDs, and audit timestamps.
- **Auditable Stock Adjustments:** Idempotent manual and operational inventory corrections supporting cycle count, damage, shrinkage, expiry, and restock corrections.
- **Reservations & Scheduled Expiry:** Account-owned stock reservations with optimistic concurrency, pessimistic locking on balance checks, manual confirm/release, and an automated background scheduler that expires overdue holds every 60 seconds.
- **Multi-Location Transfers:** State-machine transfer lifecycle (`CREATED` $\rightarrow$ `APPROVED` $\rightarrow$ `DISPATCHED` $\rightarrow$ `IN_TRANSIT` $\rightarrow$ `PARTIALLY_RECEIVED` / `RECEIVED` / `CANCELLED`) with source dispatch reservations, transit tracking, and damaged quantity reconciliation.
- **Procurement & Goods Receipt:** Supplier registry with lead times, Purchase Order lifecycle (`DRAFT` $\rightarrow$ `APPROVED` $\rightarrow$ `ORDERED` $\rightarrow$ `PARTIALLY_RECEIVED` / `RECEIVED`), and idempotent Goods Receipt Notes (GRN) automatically crediting on-hand inventory.
- **Walmart M5 Demand Forecasting:** Feature-engineered pipeline trained on 210,600 historical store-SKU records, utilizing Scikit-Learn `HistGradientBoostingRegressor` with Poisson loss. Evaluated on a strictly held-out 28-day window with zero future data leakage.
- **Replenishment Engine:** Reorder Point (ROP) calculation incorporating average daily demand, lead times, safety stock factors, and on-hand/incoming supply deductions. Deduplicates recommendations and enables one-click PO conversion.
- **React 19 Operations Dashboard:** Responsive Single Page Application built with Vite, Tailwind CSS 4, Recharts, and Lucide icons, providing 11 operational views for store managers and supply chain directors.
- **Docker Compose Orchestration:** Full-stack containerized setup with healthchecks, environment security validation, and persistent PostgreSQL storage.

---

## System Architecture

```
┌────────────────────────────────────────────────────────────────────────┐
│                        React 19 + TypeScript SPA                       │
│             (Vite 6 / Tailwind CSS 4 / Recharts / Lucide)              │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │ HTTP / REST (JWT Bearer)
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│                    Nginx 1.27 Reverse Proxy (:3000)                    │
│             Proxies /api/v1/ and /actuator/ to Spring Boot             │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│                   Spring Boot 4.1.1 Backend (:8080)                    │
│          Java 21 / Spring Security / Spring Data JPA / Flyway          │
└───────────────────┬────────────────────────────────┬───────────────────┘
                    │                                │ HTTP / REST (JSON)
                    │ JDBC (HikariCP)                ▼
                    ▼               ┌────────────────────────────────────┐
┌────────────────────────────────┐  │     FastAPI ML Service (:8001)     │
│       PostgreSQL 16 DB         │  │     Python 3.12 / Scikit-Learn     │
│   (Persistent Docker Volume)   │  │   Walmart M5 Forecasting Model     │
└────────────────────────────────┘  └────────────────────────────────────┘
```

Detailed architectural and API documentation:
- [System Architecture](docs/ARCHITECTURE.md)
- [REST API & ML Service Contract](docs/API.md)
- [Dataset Documentation](docs/DATASET.md)
- [ML Pipeline & Evaluation Results](docs/ML_PIPELINE.md)
- [Setup & Deployment Guide](docs/SETUP.md)

---

## Technology Stack

- **Backend:** Java 21, Spring Boot 4.1.1, Spring Data JPA / Hibernate (`ddl-auto: validate`), Flyway (`V1`–`V11`), Spring Security (JWT HS256, BCrypt, RBAC), Spring Actuator, PostgreSQL 16.
- **ML & Forecasting Service:** Python 3.12 / 3.14, Pandas, NumPy, Scikit-Learn (`HistGradientBoostingRegressor` with Poisson loss), Joblib, FastAPI, Uvicorn, Pytest.
- **Frontend:** React 19, TypeScript 5.7, Vite 6, Tailwind CSS 4, React Router 7, Axios, Recharts, Lucide React, Vitest, Testing Library.
- **Infrastructure:** Docker, Docker Compose, Nginx 1.27-alpine.

---

## Core Domain Model

- **Product:** Identifies sellable SKUs with category, name, and unit cost/pricing.
- **Location:** Physical node in the supply network (`WAREHOUSE`, `DISTRIBUTION_CENTER`, `STORE`).
- **Inventory:** Tracks stock at a specific `(product_id, location_id)` with `on_hand`, `reserved`, and `@Version` concurrency control.
- **InventoryTransaction:** Immutable record of every stock movement (`CYCLE_COUNT`, `DAMAGE`, `TRANSFER_DISPATCH`, `GOODS_RECEIPT`, etc.).
- **Reservation:** Time-bounded hold locking available stock for customer orders or order staging.
- **InventoryTransfer:** Governs inter-location stock movements across routes.
- **Supplier:** Vendor registry tracking contact details and expected lead times.
- **PurchaseOrder:** Procurement document tracking items, quantities, unit costs, and delivery status.
- **ReplenishmentRecommendation:** System-generated suggestion to reorder when effective inventory drops below reorder point.
- **DemandForecast:** Persisted prediction horizon (1–28 days) generated by the ML service.

---

## Inventory Management & Immutable Ledger

### Availability Invariant
Available stock is always computed dynamically:
$$\text{available} = \text{onHand} - \text{reserved}$$

PostgreSQL CHECK constraints enforce that `on_hand >= 0`, `reserved >= 0`, and `reserved <= on_hand`.

### Immutable Ledger
Every stock state change writes an `InventoryTransaction`:
- Quantities altered (`on_hand_delta`, `reserved_delta`).
- Transaction category and business reason.
- Reference ID pointing to the originating reservation, transfer, adjustment, or goods receipt.

---

## Reservation Lifecycle

```
               [ Create Hold ]
                      │
                      ▼
               (  ACTIVE  ) ────[ Expired after 10m ]────► ( EXPIRED )
                      │                                       │
         ┌────────────┴────────────┐                          ▼
         ▼                         ▼                     [ Release ]
   [ Customer Pay ]          [ Cancellation ]                 │
         │                         │                          ▼
         ▼                         ▼                     ( RELEASED )
   ( CONFIRMED )             ( RELEASED )
         │                         │
         ▼                         ▼
   [ Deduct On-Hand ]        [ Release Reserved ]
```

---

## Demand Forecasting & Stockout Prediction

ShelfSense integrates a genuine machine-learning forecasting engine trained on the **Walmart M5 Forecasting Accuracy** dataset:
- **Features (20 total):** Lags ($t-1, t-7, t-14, t-28$), pre-shifted rolling statistics (7-day, 14-day, 28-day means and standard deviations), calendar features (day of week, month, day of month, weekend, event flag, SNAP eligibility), and pricing signals.
- **Zero Data Leakage:** All rolling metrics are calculated strictly with `shift(1)` so future values are never visible during feature generation.
- **Held-Out Test Results (28 Days):**
  - **MAE:** `0.8561` (Baseline 7d Moving Avg: `0.8695`, Seasonal Naive: `1.0210`)
  - **RMSE:** `1.6315` (Baseline 7d Moving Avg: `1.7134`, Seasonal Naive: `2.2177`)
  - **WAPE:** `78.82%` (Baseline 7d Moving Avg: `80.05%`, Seasonal Naive: `93.99%`)
  - **Bias:** `+0.0281` (Well-calibrated, zero systematic skew)

---

## Replenishment Engine

The replenishment service computes safety stock and Reorder Points (ROP):
$$\text{Safety Stock} = Z \times \sigma_{\text{demand}} \times \sqrt{\text{Lead Time}}$$
$$\text{ROP} = (\text{Avg Daily Demand} \times \text{Lead Time}) + \text{Safety Stock}$$
$$\text{Effective Stock} = \text{On-Hand} - \text{Reserved} + \text{Incoming Supply}$$

When $\text{Effective Stock} \le \text{ROP}$, a deduplicated `ReplenishmentRecommendation` is raised with suggested order quantity. Administrators can approve the recommendation with one click to generate a linked `PurchaseOrder`.

---

## API Design

| Method | Endpoint | Access | Purpose |
|---|---|---|---|
| `POST` | `/api/v1/auth/register` | Public | Register a new user account |
| `POST` | `/api/v1/auth/login` | Public | Authenticate with credentials; returns JWT token |
| `GET` | `/api/v1/auth/me` | Authenticated | Retrieve profile and assigned role of current user |
| `GET` | `/api/v1/dashboard/summary` | Authenticated | Operations KPI summary (inventory, alerts, orders) |
| `GET` | `/api/v1/products` | Authenticated | List products with pagination and category filtering |
| `POST` | `/api/v1/products` | `ADMIN` | Create a new SKU |
| `GET` | `/api/v1/locations` | Authenticated | List warehouse, distribution, and store locations |
| `POST` | `/api/v1/locations` | `ADMIN` | Register a new supply chain node |
| `GET` | `/api/v1/inventory` | Authenticated | Query stock levels and low-stock alerts |
| `POST` | `/api/v1/inventory/adjustments` | `ADMIN` | Record auditable stock adjustment with reason |
| `GET` | `/api/v1/inventory/transactions` | Authenticated | Query immutable audit ledger |
| `POST` | `/api/v1/reservations` | Authenticated | Create time-bounded stock reservation |
| `PUT` | `/api/v1/reservations/{id}/confirm`| Authenticated | Confirm reservation and deduct on-hand inventory |
| `PUT` | `/api/v1/reservations/{id}/release`| Authenticated | Release reservation and return stock to available |
| `GET` | `/api/v1/transfers` | Authenticated | List multi-location stock transfers |
| `POST` | `/api/v1/transfers` | `ADMIN` | Create an inter-location stock transfer |
| `PUT` | `/api/v1/transfers/{id}/approve` | `ADMIN` | Approve transfer request |
| `PUT` | `/api/v1/transfers/{id}/dispatch`| `ADMIN` | Dispatch stock and decrement source on-hand |
| `PUT` | `/api/v1/transfers/{id}/receive` | `ADMIN` | Record transfer arrival with damaged quantity tracking |
| `GET` | `/api/v1/suppliers` | Authenticated | List registered suppliers and lead times |
| `POST` | `/api/v1/suppliers` | `ADMIN` | Register a new vendor |
| `GET` | `/api/v1/purchase-orders` | Authenticated | List purchase orders |
| `POST` | `/api/v1/purchase-orders` | `ADMIN` | Create a draft purchase order |
| `PUT` | `/api/v1/purchase-orders/{id}/approve` | `ADMIN` | Approve purchase order |
| `PUT` | `/api/v1/purchase-orders/{id}/order` | `ADMIN` | Mark purchase order as sent to vendor |
| `POST` | `/api/v1/purchase-orders/{id}/receipts`| `ADMIN` | Record verified goods receipt and increment stock |
| `POST` | `/api/v1/replenishment/generate` | `ADMIN` | Trigger inventory re-evaluation and ROP check |
| `GET` | `/api/v1/replenishment` | Authenticated | View active replenishment recommendations |
| `PUT` | `/api/v1/replenishment/{id}/approve` | `ADMIN` | Approve recommendation into a purchase order |
| `GET` | `/api/v1/forecasts/model-info` | Authenticated | View live ML model status and evaluation metrics |
| `POST` | `/api/v1/forecasts/generate` | Authenticated | Run live demand inference for SKU and location |
| `GET` | `/api/v1/accounts` | `ADMIN` | List user accounts |
| `PATCH`| `/api/v1/accounts/{id}/status` | `ADMIN` | Enable or disable user account |

---

## Database Design & Migrations

Database schema migrations are managed via **Flyway**:
- `V1`: Core schema tables (`products`, `locations`, `inventory`, `inventory_transactions`).
- `V2`: Audit timestamp triggers and CHECK constraints.
- `V3`: Reservations table and foreign keys.
- `V4`: Outbox pattern table for distributed messaging.
- `V5`: Reservation status index and expiry optimization.
- `V6`: Accounts and authentication table (`accounts`).
- `V7`: Initial admin bootstrap configuration.
- `V8`: Reservation account ownership (`account_id`).
- `V9`: Stock adjustments table and transfer state machine (`transfers`).
- `V10`: Procurement tables (`suppliers`, `purchase_orders`, `purchase_order_items`, `goods_receipts`).
- `V11`: Forecasts and replenishment tables (`demand_forecasts`, `replenishment_recommendations`).

---

## Project Structure

```text
ShelfSense/
├── docker-compose.yml              # Root multi-container orchestration
├── Dockerfile                      # Spring Boot multi-stage build
├── pom.xml                         # Maven dependencies and build plugins
├── docs/                           # Technical documentation suite
│   ├── ARCHITECTURE.md             # System architecture & component design
│   ├── API.md                      # REST API & ML service contracts
│   ├── DATASET.md                  # Walmart M5 dataset provenance & schema
│   ├── ML_PIPELINE.md              # Model training, features & evaluation
│   └── SETUP.md                    # Detailed step-by-step developer setup
├── frontend/                       # React 19 + TypeScript SPA
│   ├── src/
│   │   ├── api/                    # Axios client and TypeScript types
│   │   ├── components/             # Navigation, guards, UI primitives
│   │   ├── context/                # AuthContext and ToastContext
│   │   ├── pages/                  # 11 operational dashboard views
│   │   └── test/                   # Vitest unit & workflow tests
│   └── nginx.conf                  # Container reverse proxy configuration
├── infrastructure/
│   └── docker-compose.yml          # PostgreSQL standalone developer container
├── ml/                             # Python demand forecasting microservice
│   ├── artifacts/                  # Serialized models & evaluation curves
│   ├── src/                        # Data prep, training, evaluation, FastAPI
│   └── tests/                      # Pytest unit & integration tests
└── src/                            # Java Spring Boot backend
    ├── main/java/com/nisatsama/    # Domain modules (auth, inventory, etc.)
    └── main/resources/             # application.yml and Flyway migrations
```

---

## Getting Started

### Option A: Full-Stack Docker Compose (Recommended)

1. Clone the repository:
   ```bash
   git clone https://github.com/nisatsama/ShelfSense.git
   cd ShelfSense
   ```

2. Copy the example environment file and set a secure 256-bit Base64 JWT secret:
   ```powershell
   Copy-Item .env.example .env
   # Generate and set SHELF_SENSE_JWT_SECRET in .env (openssl rand -base64 32)
   ```

3. Launch the complete platform:
   ```powershell
   docker compose up --build -d
   ```

4. Verify services:
   ```powershell
   docker compose ps
   ```

5. Access the applications:
   - **Frontend Dashboard:** [http://localhost:3000](http://localhost:3000)
   - **Backend API:** [http://localhost:8080](http://localhost:8080)
   - **Actuator Health:** [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health)
   - **ML Forecasting Microservice:** [http://localhost:8001/health](http://localhost:8001/health)

---

### Option B: Local Development

For step-by-step local developer setup instructions across PostgreSQL, the Python ML microservice, the Spring Boot backend, and the Vite frontend, consult the [Setup Guide](docs/SETUP.md).

---

## Running Tests

### 1. Backend Test Suite (Spring Boot & PostgreSQL)
```powershell
.\mvnw.cmd test
```
*Executes all 54 integration and unit tests covering RBAC, reservations, optimistic concurrency, transfers, procurement, and ML client communication.*

### 2. ML Forecasting Test Suite (FastAPI & Scikit-Learn)
```powershell
cd ml
.\.venv\Scripts\pytest.exe -v
```
*Executes all 12 Pytest unit and integration tests verifying feature engineering, API contracts, and model inference in < 5 seconds.*

### 3. Frontend Test Suite (React & Vitest)
```powershell
cd frontend
npm test
npm run build
```
*Executes all 12 Vitest component and workflow tests across all 11 pages, followed by a clean TypeScript production bundle build.*

---

## Engineering Roadmap

| Phase | Milestone | Scope | Status |
|---|---|---|---|
| **1** | Core Domain & PostgreSQL Persistence | Products, locations, inventory schema, Flyway migrations | **Implemented** |
| **2** | Stock Availability & Invariants | On-hand, reserved, derived available, database constraints | **Implemented** |
| **3** | Reservation Lifecycle | Create, confirm, release, optimistic locking | **Implemented** |
| **4** | Concurrency & Scheduled Expiry | Pessimistic reservation locking, background expiry task | **Implemented** |
| **5** | Security, RBAC & Ownership | JWT HS256 auth, account ownership, user reservation scoping | **Implemented** |
| **6** | Immutable Ledger & Adjustments | `inventory_transactions`, auditable adjustments with reasons | **Implemented** |
| **7** | Multi-Location Transfers | Inter-node transfer state machine, damage reconciliation | **Implemented** |
| **8** | Procurement & Purchasing | Suppliers, Purchase Orders, idempotent Goods Receipts | **Implemented** |
| **9** | Walmart M5 ML Pipeline | Real M5 dataset training, Poisson regression, leak-free pipeline | **Implemented** |
| **10** | FastAPI Prediction Service | Autoregressive inference API, model metadata & health | **Implemented** |
| **11** | Replenishment Engine | Safety stock, ROP, deduplicated recommendations, PO linkage | **Implemented** |
| **12** | React Operations Dashboard | 11 responsive SPA views, Recharts analytics, JWT auth guards | **Implemented** |
| **13** | Docker Compose Orchestration | Multi-container setup with healthchecks and persistent storage | **Implemented** |
| **14** | Kafka Event Bus & Outbox | Transactional outbox event streaming to Kafka consumers | **Planned** |
| **15** | Redis Distributed Caching | Second-level caching for high-turnover product inventory | **Planned** |

---

## Design Decisions

1. **Modular Monolith for Core Business Logic:** Keeps transactional boundaries atomic and simplifies operational management while decoupling the ML service into an independent microservice.
2. **PostgreSQL as the Single Source of Truth:** Inventory balances, ledger entries, and reservation states are protected by strict relational constraints and ACID transactions.
3. **Derived Available Inventory:** Available inventory is always computed ($\text{available} = \text{onHand} - \text{reserved}$) to prevent state desynchronization.
4. **Optimistic Locking for Inventory Updates:** JPA `@Version` protects concurrent inventory mutations from dirty writes while pessimistic `SELECT ... FOR UPDATE` serializes critical reservation allocations.
5. **Zero-Leakage Time-Series Forecasting:** Temporal features and rolling aggregations strictly use past lags ($t-1$ or earlier) with pre-shifted windows to guarantee valid evaluation.
6. **Stateless JWT with Database Enablement Checks:** Enables distributed stateless verification while supporting immediate account deactivation via security filters.

---

## Contributing

Contributions, bug reports, and suggestions are welcome:
1. Fork the repository.
2. Create a feature branch (`git checkout -b feature/amazing-feature`).
3. Commit your changes with clear messages.
4. Run the full test suites (Backend, ML, and Frontend).
5. Open a Pull Request targeting `main`.

---

## License

This project is licensed under the Apache License 2.0. See the repository for full license terms.

---

## Author

**Nisat Sama**  
Java Backend Developer | Spring Boot | PostgreSQL | Distributed Systems  
- **GitHub:** [@nisatsama](https://github.com/nisatsama)  
- **Project Repository:** [ShelfSense](https://github.com/nisatsama/ShelfSense)  

*Building retail systems that prioritize transactional correctness, operational reliability, and intelligent replenishment.*
