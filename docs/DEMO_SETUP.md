# ShelfSense Local Demo Setup & Security Architecture Guide

This document provides complete instructions for standing up a fully functioning local demonstration environment of the ShelfSense platform, including access control boundaries, administrative provisioning, and idempotency guarantees.

---

## 1. Quick Start via Docker Compose

The fastest way to run all four ShelfSense services is via Docker Compose:

```bash
# 1. Copy the environment template
cp .env.example .env

# 2. Configure a secure JWT secret in .env:
# SHELF_SENSE_JWT_SECRET="your-32-plus-character-secret"

# 3. (Optional) Configure initial administrator credentials:
# SHELF_SENSE_BOOTSTRAP_ADMIN_USERNAME="admin@shelfsense.local"
# SHELF_SENSE_BOOTSTRAP_ADMIN_PASSWORD="StrongAdminPassword123!"

# 4. Start all services
docker compose up -d

# 5. Verify service health
docker compose ps
```

All 4 services will be running:
- **Frontend Dashboard**: http://localhost:3000
- **Spring Boot API**: http://localhost:8080 (Health: `http://localhost:8080/actuator/health`)
- **FastAPI ML Service**: http://localhost:8001 (Health: `http://localhost:8001/health`)
- **PostgreSQL 16**: `localhost:5432` (database: `shelfsense`)

---

## 2. Security & Role-Based Access Control (RBAC)

ShelfSense enforces strict role-based access control backed by stateless JWT authentication:

| Role | Permissions & Access Scope |
| :--- | :--- |
| **`ROLE_ADMIN`** | Full platform control: manage products, locations, suppliers, approve purchase orders, cancel orders, perform cycle count adjustments, manage user accounts. |
| **`ROLE_USER`** | Operational access: view products, inventory, reservations, submit stock reservations, view forecasts, and trigger replenishment recommendations. Cannot approve purchase orders or alter catalog definitions. |

### Access Control Rules:
1. **Public Registration**: Calls to `POST /api/v1/auth/register` automatically and strictly assign `ROLE_USER`. There is no API parameter or payload field that can grant administrative privileges through the public registration route.
2. **Initial Administrative Provisioning**:
   - Driven by `SHELF_SENSE_BOOTSTRAP_ADMIN_USERNAME` and `SHELF_SENSE_BOOTSTRAP_ADMIN_PASSWORD`.
   - Executed once on application startup via `AdminBootstrapService`.
   - Prevented from repeating by the database marker row in `admin_bootstrap_state` (migration `V7`).
   - Requires password length $\ge 12$ characters with alphanumeric and special characters.

---

## 3. Idempotency & Fault-Tolerance Guarantees

ShelfSense implements cryptographic request fingerprinting for state-mutating operations:

### 1. Stock Adjustments (`POST /api/v1/inventory/adjustments`)
- Clients may supply an optional `idempotencyKey` in the request header or JSON payload.
- Canonical request attributes (`productId`, `locationId`, `quantityDelta`, `reason`) are hashed using SHA-256 (`request_hash VARCHAR(64)`).
- **Identical Retries**: Return the previously computed adjustment record with HTTP 200 without double-deducting stock or duplicating ledger entries.
- **Mismatched Payloads**: Reusing an existing idempotency key with conflicting values (e.g. +10 vs +50, or a different product) immediately throws `ConflictException` (HTTP 409 Conflict).
- **Concurrency**: Database unique constraint `uq_inventory_adjustment_idempotency` ensures that simultaneous concurrent requests with the same key serialize cleanly with exactly one stock mutation.

### 2. Goods Receipts (`POST /api/v1/procurement/orders/{id}/receive`)
- Clients provide an `idempotencyKey` for dock shipments.
- Canonical items list (sorted purchase order item IDs and received quantities) is hashed using SHA-256 (`request_hash`).
- Replayed deliveries return the original receipt without double-incrementing on-hand inventory.
- Conflicting payload quantities under an existing receipt key return HTTP 409 Conflict.

---

## 4. Running Verification Test Suites

```bash
# 1. Backend Integration Tests (Java 21 / PostgreSQL)
./mvnw clean test

# 2. ML Forecasting Service Pytest Suite (Python 3.11+)
pytest ml/tests/ -v

# 3. Frontend Production Build & Vitest Suite (Node 20+)
cd frontend
npm test
npm run build
```
