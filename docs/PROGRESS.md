# Project Progress

Last reviewed: 2026-11-06

## Current status

- Phase 0A: **Approved for implementation by the Phase 0B request; product-spec validation remains pending.**
- Phase 0B: **Foundation implemented; automated tests pass.**
- Phase 0C: **Identity backend and initial React identity UI implemented; core backend tests pass.**
- Phase 0D: **Frontend foundation implemented; build, lint, unit tests, and browser tests are pending because npm dependency installation is blocked by repeated registry timeouts.**
- Phase 0E: **Deployment foundation files created; Docker runtime verification is blocked because Docker CLI is unavailable in this environment.**
- Phase 4: **Initial TMS vertical slice implemented; full operational validation and browser verification remain incomplete.**
- Phases 1, 2, and 5 through 11: **Not started.**
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
