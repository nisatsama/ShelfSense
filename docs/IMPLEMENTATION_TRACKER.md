# ShelfSense Implementation Tracker

Statuses: `NOT_STARTED`, `IN_PROGRESS`, `IMPLEMENTED`, `TESTED`, `VERIFIED`. Source presence alone does not mean a feature is complete. `VERIFIED` requires successful relevant checks in the current repository environment.

| Feature | Current status | Files involved | Dependencies | Required tests | Priority | Completion status |
|---|---|---|---|---|---|---|
| Git integrity and feature branch | VERIFIED | `.git`, branch metadata | GitHub clone | remote/status/history/branch review | P0 | Verified clone and feature branch from origin/main |
| Product create/list/get | IMPLEMENTED | `product/**`, V1 migration | PostgreSQL, Flyway | service/API/database tests | P0 | Unverified |
| Location create/list/get | IMPLEMENTED | `location/**`, V1 migration | PostgreSQL, Flyway | service/API/database tests | P0 | Unverified |
| Inventory create/read and derived available quantity | IMPLEMENTED | `inventory/**`, V1 migration | Product/location records | invariant and API integration tests | P0 | Unverified |
| Inventory mutation ledger | IN_PROGRESS | `InventoryTransaction.java`, enum, V3 migration; missing repository/use cases | Atomic transaction with inventory mutations | verify ledger rows for every lifecycle operation | P0 | Entity/table only; no writes |
| Reservation creation | IMPLEMENTED | `reservation/**`, inventory services, V2 migration | Inventory row | insufficient stock, positive quantity, atomic persistence tests | P0 | Unverified; no ledger/idempotency |
| Reservation retrieval | IMPLEMENTED | reservation controller/service/repository | PostgreSQL | found/not-found/API tests | P0 | Unverified |
| Reservation release | IMPLEMENTED | reservation service/controller, inventory | Locking and ledger | lifecycle, repeated release, concurrent release tests | P0 | Unverified; no lock/ledger |
| Reservation confirmation | IMPLEMENTED | reservation service/controller, inventory | Locking and ledger | lifecycle, stock decrement, concurrent confirm tests | P0 | Unverified; no lock/ledger |
| Inventory concurrency correctness | NOT_STARTED | inventory/reservation services and repositories | PostgreSQL locking/version strategy | simultaneous reservations/adjustments | P0 | Not complete |
| Idempotent write APIs | NOT_STARTED | new idempotency domain, migrations, API filter/service | Stable request key semantics | duplicate and payload mismatch tests | P0 | Not started |
| PostgreSQL integration test setup | NOT_STARTED | `pom.xml`, test config, test sources | Testcontainers or documented local DB | clean migrations + integration suite | P0 | Context test only; unable to run |
| Externalized configuration and error mapping | IN_PROGRESS | `application.yml`, common handlers, services | Environment variables, domain exceptions | validation/404/conflict/database error tests | P0 | Hardcoded password; only 400 handler |
| Reservation expiration | NOT_STARTED | reservation domain/service/scheduler, migration/index | Clock and safe state transitions | expiry boundaries and concurrent confirmation | P1 | Field/status exist; no behavior |
| Transactional outbox | NOT_STARTED | new outbox model/migration/services | Atomic write boundary | rollback/publisher restart tests | P1 | Not started |
| Kafka publication/consumers | NOT_STARTED | configuration, event handlers | Outbox, Kafka | duplicate, invalid event, failure/retry tests | P1 | Not started |
| Redis cache | NOT_STARTED | cache configuration/use cases | Explicit cache targets and invalidation rules | cache hit/miss/failure tests | P1 | Not started |
| Sales and demand aggregation | NOT_STARTED | new sales/demand modules and migrations | Product/location/inventory | validation and aggregation tests | P1 | Not started |
| Forecasting and stockout prediction | NOT_STARTED | new forecasting module | Demand history and lead-time inputs | deterministic forecast/stockout tests | P1 | Not started |
| Replenishment recommendations | NOT_STARTED | new replenishment module | Forecasts, stock, lead times | threshold and explainability tests | P1 | Not started |
| Warehouse transfers | NOT_STARTED | new transfer module/migrations | Inventory ledger, transactional workflow | no duplication/partial failure tests | P2 | Not started |
| Suppliers and purchase orders | NOT_STARTED | supplier/purchase order modules | Location, deliveries, inventory receipt | lifecycle and receipt tests | P2 | Not started |
| Authentication, RBAC, location scope | NOT_STARTED | security configuration, user/role schema | Identity and authorization policy | endpoint and cross-location denial tests | P2 | Not started |
| Operations frontend | NOT_STARTED | new frontend project | Authenticated backend APIs | frontend build and API/error-state tests | P2 | Not started |
| Audit trail and notifications | NOT_STARTED | audit/notification modules | Security and event model | access and delivery failure tests | P2 | Not started |
| Observability and resilience | IN_PROGRESS | actuator dependency, config, logging/metrics | Operational metric definitions | health/metrics and degradation tests | P3 | Actuator dependency only |
| Docker reproducibility | IN_PROGRESS | `infrastructure/docker-compose.yml` | Health checks and app runtime | clean start/readiness/migration checks | P3 | PostgreSQL service only |
| CI/CD | NOT_STARTED | `.github/workflows/**` | Build/test/runtime configuration | workflow execution on clean clone | P3 | No workflow present |
| Load and failure testing | NOT_STARTED | `performance/**`, tests, docs | Stable APIs and environment | k6 scenarios, real measured results | P3 | Not started |
| Engineering documentation and ADRs | IN_PROGRESS | `docs/**`, README | Verified implementation decisions | link/content review | P3 | Database design exists; audit/tracker added |
