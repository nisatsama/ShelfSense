# ShelfSense Project Audit

**Audit date:** 2026-11-16  
**Scope:** Current checkout inspected before application changes.

## Executive summary

ShelfSense is an early-stage Spring Boot modular monolith. It has CRUD-style create/read APIs for products and locations, inventory creation/read, and reservation create/read/release/confirm. PostgreSQL and Flyway are configured, and the schema captures core inventory quantity constraints. The implementation is a useful foundation, but there is no evidence yet that the app compiles or starts against PostgreSQL: the single context test could not be run because the Maven wrapper attempted to install Maven outside the writable workspace.

The original extracted directory is a ZIP archive without `.git`. A separate sibling Git checkout has now been cloned from `https://github.com/nisatsama/ShelfSense.git`. Its `origin/main` base is commit `e8082dd70e072553ed7d2dabff7b54f4424ac99a`; the requested local feature branch was created from that base. The extracted ZIP remains untouched.

## Current architecture and modules

- Java 21 configured in Maven; Spring Boot parent is 4.1.1.
- One application entry point under `com.nisatsama.shelfsense`.
- Domain packages: `product`, `location`, `inventory`, `reservation`, and `common`.
- Controllers call services; Spring Data JPA repositories persist entities; request and response records define basic API DTOs.
- PostgreSQL is configured as the relational store. Flyway owns three sequential SQL migrations. Hibernate uses `ddl-auto: validate` and open-in-view is disabled.
- Docker Compose currently defines only PostgreSQL.
- No frontend, CI workflow, security configuration, messaging, cache, or deployment manifests were found.

## Implemented features

- Product create/list/get with SKU uniqueness check and request validation.
- Location create/list/get with code uniqueness check and enum location types.
- Inventory creation and lookup by product/location; available quantity is derived as on-hand minus reserved.
- Reservation create/get/release/confirm endpoints and ACTIVE/CONFIRMED/RELEASED/EXPIRED states.
- Database constraints for unique SKU/location codes/product-location inventory, nonnegative stock/reservations, reservation bounds, positive reservation quantity, and known enum strings.
- Inventory and product entities carry optimistic version fields, but concurrency behavior has not been verified.
- Consistent response shape exists for a narrow `IllegalArgumentException` handler.

## Partial, broken, or unverified functionality

- Reservation operations adjust inventory and status in a service transaction, but do not create the corresponding inventory ledger records. The ledger entity and V3 table exist without a repository or write path.
- Reservation concurrency is unsafe/unverified: inventory is read then modified without an explicit lock or conditional update. `@Version` may detect conflicts, but there is no retry/translation strategy or test; concurrent calls may fail rather than provide a defined API outcome.
- Release and confirm fetch reservation without a lock/version. Concurrent state transitions are not proven safe.
- Create reservation validates related product and location, but the inventory query already identifies those relationships. Expiration is represented in the model but never assigned or processed.
- Domain failures are mapped to HTTP 400, including missing entities; there is no 404 mapping. Database constraint/optimistic locking/validation errors are not normalized.
- Creation duplicate checks are vulnerable to races; database unique constraints are the final guard, but resulting errors are not handled consistently.
- Static code appears internally aligned with the migration definitions, but runtime schema validation and clean-database migration execution remain unverified.
- The Spring context test requires a reachable PostgreSQL database because no test profile, embedded database, or Testcontainers setup exists.

## Database schema status

Migrations `V1` through `V3` create `products`, `locations`, `inventory`, `reservations`, and `inventory_transactions`. Foreign keys, check constraints, uniqueness, and several useful indexes exist. The inventory `(product_id, location_id)` uniqueness constraint supports its lookup. Reservation indexes are on product/location and status separately; an index aligned to common status/expiry scans may be needed when expiration is implemented. The transaction ledger stores type, quantity, optional reference ID, and timestamp, but has no mutation linkage beyond inventory and is not currently populated. No migration has been verified against a running clean PostgreSQL instance.

## Tests and coverage

- One test: `ShelfsenseApplicationTests.contextLoads`.
- No unit tests for domain calculations or service behavior.
- No controller, repository, integration, migration, concurrency, or security tests.
- Test execution was attempted with `mvnw.cmd test -q`; the wrapper failed before Maven started because it could not create its Maven install directory outside the writable workspace. No test result is available.

## Security, dependency, and operational review

- No Spring Security dependency/configuration, authentication, authorization, or location scoping.
- `application.yml` contains a development database password (`1234`) directly in configuration; replace it with environment-based configuration before committing to a real Git repository or deployment.
- CORS, rate limiting, secure headers, secret management, and audit logging are absent.
- Dependency versions are controlled primarily by Spring Boot 4.1.1. No frontend, Kafka, Redis, or resilience dependencies are present. Do not add them until a concrete phase needs them. Compatibility and vulnerability status were not externally checked.
- Actuator is included, but exposure and security settings are not explicit. No metrics backend, tracing, structured logging setup, or dashboards exist.
- Docker Compose supplies PostgreSQL 16 only, with a persistent volume but no health check. Application startup is not containerized.
- No CI, deployment workflow, load tests, or operational runbook. Deployment readiness is low.

## Technical debt and what to preserve

Preserve the modular package boundaries, DTO-based HTTP boundary, Flyway-first schema ownership, `ddl-auto: validate`, derived available inventory, database-level quantity invariants, and the existing narrow CRUD APIs. Avoid rewriting the application or introducing distributed infrastructure before reservation correctness is established.

Refactor incrementally: introduce domain-specific exceptions/status mapping; make inventory mutations produce ledger rows atomically; formalize reservation state transitions; add a concurrency strategy and tests; externalize database configuration; then add migration-backed test infrastructure. Do not implement microservices, forecasting, Kafka, Redis, frontend, or production deployment in the first correctness milestone. Do not claim any existing feature is verified until it can compile and pass relevant tests.

## Recommended sequence and estimate

1. Restore a real Git checkout, inspect user changes/history, and create the requested development branch safely. (Blocked by absent `.git` here.)
2. Make configuration environment-driven and establish a repeatable backend build/test setup with PostgreSQL-backed integration tests.
3. Complete P0 inventory/reservation correctness: atomic mutation + ledger, locking/version handling, state-transition safety, consistent errors, then concurrent integration tests.
4. Add idempotency and reservation expiration with tests.
5. Add outbox/eventing only when core mutations are proven; Kafka follows outbox and consumer idempotency design.
6. Add sales/demand data and explainable forecasts/replenishment with deterministic tests.
7. Add RBAC and scoped authorization before exposing multi-location operations.
8. Add UI, observability, reproducible infrastructure, CI, load testing, and deployment documentation based on demonstrated needs.

Remaining work is a multi-phase project, not a 100-commit target. A credible first P0 verification milestone is approximately 5–8 small reviewable commits after restoring Git and enabling tests. Full portfolio scope is likely dozens of meaningful milestones; commit count should follow actual independently verifiable work.

## Repository and environment findings

- The extracted ZIP root has no `.git` directory. The separate clone has verified `origin` URL, clean initial status, `main`/`origin/main` at `e8082dd`, and a local feature branch `feature/shelfsense-engineering-completion`.
- Java runtime detected: Temurin OpenJDK 25.0.5; the Maven project requests Java 21. The wrapper is configured for Maven 3.9.16 and honors `MAVEN_USER_HOME`; verification with a clone-local Maven home is recorded in the Phase 0.1 report.
- No source files were changed during the audit. Only this audit and its tracker are being added.

## Phase 0.1 restoration

- GitHub URL verified as the public repository linked by the user. The clone remote is https://github.com/nisatsama/ShelfSense.git and default/base branch is main.
- A file hash comparison found 47 common files byte-identical. The only ZIP-only files are this audit and IMPLEMENTATION_TRACKER.md; no shared file content differs. Both documents were copied into the clone for continuity. No generated files or secrets were copied.
- Inspection: public repository, clone remote, branch history, file hashes, and environment configuration were inspected. Configured: Maven wrapper used a clone-local cache. Tested: `mvnw.cmd -q -DskipTests test-compile` exited 0, compiling main and test sources. Not tested: JUnit execution, application startup, Flyway against PostgreSQL, or Docker Compose, because PostgreSQL is not listening and Docker daemon is unavailable. Verified: Git branch/remote/status and project source compilation; working database behavior remains unverified.
