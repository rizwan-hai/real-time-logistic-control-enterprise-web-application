# Project Progress

Last reviewed: 2026-11-08

## Current status

- Phase 0A: **Approved for implementation by the Phase 0B request; product-spec validation remains pending.**
- Phase 0B: **Foundation implemented; automated tests pass.**
- Phase 0C: **Identity backend and initial React identity UI implemented; core backend tests pass.**
- Phase 0D: **Frontend foundation implemented; build, lint, unit tests, and browser tests are pending because npm dependency installation is blocked by repeated registry timeouts.**
- Phase 0E: **Deployment foundation files created; Docker runtime verification is blocked because Docker CLI is unavailable in this environment.**
- Phase 4: **Initial TMS vertical slice implemented; full operational validation and browser verification remain incomplete.**
- Phase 5: **Substantial tracking slice implemented; acceptance is not complete.** Device-authenticated batch GPS ingestion, async batch persistence, event idempotency, monthly event-time partitioning, Redis latest-position projection, tenant-scoped STOMP updates, geofences, ETA estimates, basic exception rules and public tracking links are present. The Control Tower includes fleet/alert/geofence views and trip-history replay. Real map tiles/clustering, rule tuning/notifications, sustained benchmark evidence and production MySQL/Redis/WebSocket verification remain incomplete.
- Phase 8: **Notification infrastructure implemented; acceptance is not complete.** Notification records/preferences/templates/delivery logs, outbox consumption and deduplication, English/Persian rendering, per-user REST and STOMP authorization, retries/dead-letter handling, and the notification center are present. Order, trip dispatch, ETA, GPS alert, idle, and geofence events are wired; other event types are consumed when their owning business modules publish them. Production SMS/Web Push adapters, browser build verification, and SPEC reconciliation remain outstanding.
- Phase 9: **Initial reporting slice implemented; acceptance is not complete.** Tenant/customer/warehouse-scoped dashboards, date filters, delivery/fleet/warehouse and trip-expense aggregates, daily shipment trends, paged delivery drill-down, CSV/streaming XLSX/PDF asynchronous export jobs, status/download authorization, recurring schedules, and role-specific ECharts dashboards are present. Financial ledger/invoice, reorder thresholds, inventory valuation, availability calendars, and dedicated delivery-attempt source data are absent, so those KPIs remain explicitly unavailable. Build/test/browser verification and reconciliation with the missing `docs/SPEC.md` §13 remain pending.
- Phases 1, 2, 6, 7, and 10–11: **Not started.**
- Phase 3: **WMS implementation started; incomplete.**
- Application implementation: **Backend foundation, identity/authentication, initial order/shipment workflows, and partial WMS vertical slice.** Other business-domain workflows remain unimplemented.
- Phase 4 includes trip lifecycle/assignment APIs and history, shipment trip linking and reassignment, trip expenses/fuel logs, carrier tender invite/quote/award, routing interfaces with a Haversine estimate adapter, a nearest-neighbor/2-opt heuristic, a pure operational validation policy, and a dispatch board with dnd-kit shipment assignment. Vehicle/load/license/hour checks are available through an explicit validation endpoint and currently rely on caller-supplied snapshots. OSRM/Google adapters, map visualization, stop reordering, automatic route-constraint integration, and full tender/delivery UX are not yet implemented.
- Source validation: **Pending** because `.github/copilot-instructions.md` and `docs/SPEC.md` are absent. Foundation follows the available approved design set and should be reconciled when those sources are restored.

## Phase 3 WMS implementation status

- [x] Added initial warehouse/inventory schema, tenant-scoped warehouse API, inventory dashboard/search, movement history, receipt quality decisions, lot/serial/expiry fields, capacity checks, putaway, FEFO/FIFO allocation, partial reservations, picking scans, package creation, count entry/approval, and compensating movement endpoint.
- [x] Order cancellation now emits the versioned `InventoryReservationReleaseRequested` outbox contract alongside the order status event.
- [ ] Phase 3 acceptance is **not complete**. The workspace has no `docs/SPEC.md`; outbox event delivery/consumer runtime is not implemented, so confirmation/cancellation events are persisted but not automatically consumed. Purchase-order/ASN management, putaway compatibility policy administration, reservation expiry worker, low/reorder/expiry alert generation, pick waves, shipping handover, camera barcode capture, and Code128/QR label PDF generation remain incomplete.
- [ ] Automated verification was not run for Phase 3. `mvn -DskipTests compile` succeeded; MySQL migration execution and frontend build remain unverified.

## Phase 0A deliverables

- [x] Inspect available workspace files; required source files were not present.
- [x] Record proposed modular monolith, module boundaries, data model, API/security, integrations and deployment architecture.
- [x] Document lifecycle proposals, permission matrix, errors, NFR assumptions, risks, dependencies and traceability.
- [x] Add minimum entity dictionary, GPS partitioning proposal, offline sync and financial consistency contracts, and 12 proposed ADRs.
- [ ] Validate every deliverable against `docs/SPEC.md` and `.github/copilot-instructions.md` once supplied.
- [x] Approve start of Phase 0B.

## Phase 0B deliverables

- [x] Java 21 / Spring Boot 3 Maven backend with dev, staging, prod, and test profiles.
- [x] Package-by-feature foundation and shared audited persistence base with UUID IDs and optimistic version field.
- [x] Flyway V1 infrastructure tables for NumberSequence, Setting, AuditLog, Attachment, and OutboxEvent; no identity tables.
- [x] RFC 7807-compatible errors, success envelope, validation, pagination/sorting/filter parsing, MapStruct configuration, OpenAPI, Actuator, Micrometer, structured logging, correlation IDs, Redis rate limiting, and MinIO client configuration.
- [x] Context, migration, API error, pagination/filter, correlation, and external-client configuration tests.
- [ ] Validate foundation against missing `.github/copilot-instructions.md` and `docs/SPEC.md` when available.

## Phase 8 notification implementation status

- [x] Added notification, template, preference, delivery-log, event-receipt, and ETA-snapshot schemas with English and Persian defaults.
- [x] Added idempotent transactional outbox consumption, per-user delivery jobs, exponential retries, dead-letter status, preference-based channel selection, and delivery history.
- [x] Added in-app, authorized WebSocket, SMTP email, and local development SMS/Web Push adapters behind a provider interface.
- [x] Added actor-scoped notification APIs, unread/read actions, preference management, history, a notification bell, real-time refresh, and deep links.
- [x] Wired order status, shipment assignment, trip dispatch, ETA, GPS, idle, delivery-delay, and geofence events. The consumer also supports document expiry, inventory, POD, invoice, and payment event contracts.
- [x] Backend tests cover duplicate suppression, language selection, sensitive-content exclusion, provider failure, retries, dead-letter status, REST scoping, and WebSocket destination authorization.
- [ ] Full Phase 8 acceptance remains pending: `docs/SPEC.md` is absent, source workflows for several event types do not exist yet, production SMS/Web Push providers are deployment-specific, and frontend tests/build could not run because npm dependencies are unavailable and the local cache lacks packages.

## Phase 9 reporting implementation status

- [x] Added indexed report-job/schedule schema and organization/date-filtered delivery, fleet, warehouse, and trip-expense aggregates.
- [x] Added customer-claim scoping, assigned-warehouse scope enforcement, role-specific response groups, bounded drill-down, and daily status trends.
- [x] Added asynchronous CSV, Apache POI streaming XLSX, and PDF delivery exports with requester-scoped status/downloads, expiry cleanup, and daily/weekly/monthly schedules.
- [x] Replaced placeholder staff/customer home dashboards with ECharts KPI, trend, status, performance, inventory, and financial views, plus date/filter controls.
- [ ] Phase 9 acceptance remains incomplete. Invoice/payment and payable data, inventory valuation/reorder thresholds, vehicle availability, and a dedicated delivery-attempt record are absent; associated KPIs are returned unavailable. Frontend install/build and focused tests are pending, and `docs/SPEC.md` §13 is not present for source reconciliation.

## Evidence and constraints

`mvn test` passed: 10 tests covering random-port application startup, API envelopes and Problem Details, correlation ID propagation, request validation, pagination/filter parsing, Redis/MinIO client configuration, and Flyway migration execution in H2 MySQL mode. Docker and a live MySQL service were unavailable, so a real MySQL migration run was not performed. Identity schema remains reserved for Phase 0C. A requirements traceability matrix is maintained in [REQUIREMENTS_TRACEABILITY.md](REQUIREMENTS_TRACEABILITY.md); without the missing spec it remains provisional. See [OPEN_QUESTIONS_AND_RISKS.md](OPEN_QUESTIONS_AND_RISKS.md).

## Phase 0C deliverables

- [x] Identity schema and entities for users, roles, permissions, assignments, sessions, refresh tokens, password reset/history, recovery codes, and login audit.
- [x] JWT access tokens (15 minutes), rotating opaque refresh tokens (7 days), reuse detection, active-session checks on bearer requests, logout/session revocation, BCrypt password storage, password history, and exponential account lockout.
- [x] TOTP setup/verification and recovery codes; password reset delivery by email.
- [x] Cookie CSRF protection, secure cookie flags by profile, API security, permission evaluation, Redis permission caching, and customer/driver/warehouse data-scope helper.
- [x] Tenant-scoped user/role/permission administration APIs and development-only seeded roles/permissions/admin.
- [x] React login, forgot/reset password, MFA login/activation, profile, sessions, user activation, role view, permission matrix, and access-denied pages; Playwright specs scaffolded.
- [x] Backend tests for startup/migrations, CSRF, login success/failure, lockout, refresh rotation/reuse, logout and immediate access-token invalidation, password reset/history, RBAC grants/scopes, TOTP challenge/recovery login, and RFC6238 vector.
- [ ] Frontend dependency installation, production build, and Playwright run. The npm registry request fails with `ECONNRESET` even when retried outside the sandbox; package versions are pinned to the Vite 8 / React plugin 6 line.
- [ ] Full SPEC/security architecture validation. `docs/SPEC.md` and `.github/copilot-instructions.md` are absent from the workspace, so requirements alignment remains provisional.

Latest backend evidence: `mvn test` passes all 22 tests, including identity schema validation, authentication integration, active-session revocation, MFA challenge handling, and object scope. MySQL production behavior and browser E2E remain unverified. Password reset delivery currently uses the configured SMTP provider and only sends to email-enabled accounts; an SMS provider is not configured for phone-only accounts.

## Phase 5 tracking implementation status

- [x] Device-token authenticated batch ingestion with coordinate, timestamp-skew, speed, heading, payload-size, per-device request-rate, and retry-key checks. Database position and dedupe writes use JDBC batch inserts; processing is asynchronous.
- [x] GPS schema with a composite key, MySQL monthly `recorded_at` partitions, device/trip/vehicle/driver metadata, SRID 4326 point column, UTC event/receive timestamps, and H2-compatible migration path.
- [x] Redis latest-position projection with database fallback, post-commit cache/broker updates, organization-scoped STOMP topics, and role/trip authorization.
- [x] Circular/polygon geofence evaluation with validated shapes and stable-state debounce; ENTER/EXIT alerts; alert acknowledgment, assignment and history; rule-based route deviation, idle, speeding, offline and late-delivery-risk alerts; speed-adjusted remaining-distance ETA; expiring 256-bit public tokens, limited public shipment data and token revocation.
- [x] Control Tower fleet coordinate view/table, polling and tenant-scoped STOMP updates, geofence creation, alert center, vehicle details, trip-history timeline/replay and public tracking page. GPS simulator supports multiple devices, request batches, configurable interval, route replay, duplicate events, interruption and offered-load mode.
- [ ] Configurable rule-management UI and external notification delivery; route geometry improvements; map tiles/clustering and editable geofences; driver-name authorization policy; MySQL spatial index/partition-drop and legal-hold policy; sustained-throughput benchmarking; and production DB/Redis/WebSocket verification.
- [x] `mvn -q -DskipTests compile` and `mvn -q "-Dspring.mail.host=localhost" test` pass (32 backend tests; the system property supplies a mail host missing from the test profile). Tracking rule/geofence integration tests and a live MySQL, Redis, WebSocket, or 1,000-position/second benchmark have not been run. Frontend build is blocked because frontend dependencies, including `openapi-typescript`, are not installed. No backend throughput measurement is available; simulator output is offered client-side throughput only.

## Phase 0E deliverables

- [x] Backend Java 21 container build and frontend development/production container definitions.
- [x] Compose stack for Nginx/Vite, backend, MySQL, Redis, MinIO-compatible Silo, MailHog, Prometheus, and Grafana; health checks, persistent volumes, service networks, startup conditions, and dev/production profiles.
- [x] Nginx reverse proxy, Prometheus scrape configuration, Grafana datasource provisioning, and local environment template.
- [x] GitHub Actions CI, dependency review, CodeQL, npm audit, and container build workflows without embedded credentials.
- [x] Developer setup, environment, database initialization, and backup/restore documentation and PowerShell scripts.
- [ ] Compose startup, connectivity, reverse proxy, and frontend access checks. Docker is not installed/available in this environment.
- [ ] Frontend workflow build and lint/test. npm dependency install/lock generation cannot complete offline because the package cache lacks `@types/node` metadata; CI performs online installation.
- [ ] Local backend verification for this phase. `mvn test` failed during test compilation because Maven could not resolve existing identity/main packages from the current `target` output; `mvn clean test` also could not remove the existing `target` directory (`Access is denied`). No application source was changed in this phase.

The compose file defaults to published Silo, a community-maintained MinIO-compatible continuation, because the upstream MinIO container distribution was archived/withdrawn. Production deployment must use production secrets and a deliberate TLS/storage/backup configuration. The CI container job still needs a real API schema to build contract types and should be tightened when CI execution is available.

## Phase 0D deliverables

- [x] React 18, strict TypeScript, Vite, Ant Design 5, React Router, TanStack Query, Zustand, Axios, React Hook Form, Zod, and i18next project configuration.
- [x] Feature-based source structure, application providers, responsive shell, route guards, and staff/customer/driver/public tracking route groups.
- [x] Shared data, form, upload, status, timeline, confirmation, loading/error, boundary, and unsaved-change components.
- [x] Axios bearer/CSRF handling, serialized silent refresh, RFC 7807 error mapping, and generated OpenAPI type workflow.
- [x] English/Persian localization, RTL direction, Persian digit and Jalali date/currency formatting.
- [x] Initial Vitest coverage for shared status display, permissions, protected routing, locale formatting, and API error mapping.
- [ ] Dependency installation, production build, lint, and Playwright authentication E2E. Repeated npm installs were approved but ended with `ECONNRESET` / `EIDLETIMEOUT` while fetching the Ant Design tarball; the offline retry confirms that tarball is not cached.
- [ ] Validate against `docs/SPEC.md` and `.github/copilot-instructions.md`; both remain absent from the workspace.
