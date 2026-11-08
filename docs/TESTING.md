# Validation and testing

## Phase 11 results

| Check | Result | Evidence / limitation |
|---|---|---|
| Backend `mvn clean verify` | **Passed** | 57 tests, 0 failures, 0 errors, 0 skipped, including warehouse-scope and WebSocket-session regression tests. The suite includes H2/MySQL-mode migration tests; it does not replace a real MySQL run. |
| Frontend `npm run lint` | **Not run successfully** | Frontend dependencies were absent. Install retry failed fetching `ws` with `EHOSTUNREACH`; ESLint was unavailable. |
| Frontend `npm run test` | **Not run successfully** | Vitest dependency was unavailable. |
| Frontend `npm run build` | **Not run successfully** | `openapi-typescript` was unavailable. |
| Playwright | **Not run** | Dependencies/browser unavailable; the only checked-in spec covers mocked login and forgot-password screens, not end-to-end logistics workflows. |
| Docker image / Compose integration | **Not run** | Docker CLI unavailable. |
| Production database migration | **Not run** | No live MySQL service was available. |
| Security and dependency scans | **Not run locally** | CI defines CodeQL, npm audit, and pull-request dependency review; no result was obtained for this revision. No container scanner is configured in CI. |
| Load/performance tests | **Not run** | No API latency percentiles, database/Redis latency, report timings, WebSocket measurements, or GPS throughput results are available. |
| Backup/restore and failure drills | **Not run** | No live services or restore environment were available. |

The backend command was run from the repository root and completed with `BUILD SUCCESS`. Frontend commands must run from `frontend`; passing CI configuration is not equivalent to a CI execution.

## Required acceptance tests

Before release, run all six Playwright business workflows from the project acceptance plan, including server-side denial assertions for cross-customer, driver, warehouse, finance, and administrative access. Add/reconcile durable tests for purchase order/receipt-to-handover, failed-delivery reattempt/return, offline command replay, invoice/payment settlement, and delivery completion; these workflows are not established by the current browser test scaffold.

Run schema migrations and foreign-key checks against the supported MySQL version, exercise transaction/idempotency and concurrent-update paths, and verify database restore into an isolated environment. Run resilience drills for Redis, MinIO, database reconnect, SMTP/SMS, routing provider, WebSocket, duplicate events, outbox backlog, background-job failure, and offline synchronization.

For performance, use a documented deployment-like environment and realistic datasets. Record request definitions, concurrency/ramp, duration, error rate, and p50/p95/p99 for standard API endpoints; measure query, connection-pool, Redis, WebSocket, report, and frontend loading behavior. Benchmark GPS ingestion toward 1,000 positions/sec and record accepted/rejected throughput and resource use. Do not infer server throughput from simulator offered load.

## Reproducible commands

```powershell
mvn clean verify
Set-Location frontend
npm ci
npm run lint
npm run test
npm run build
npm run test:e2e
```

Run image and dependency scanners against the exact release candidate and retain their reports with CI artifacts.
