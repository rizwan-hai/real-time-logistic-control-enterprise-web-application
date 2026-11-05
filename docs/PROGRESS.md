# Project Progress

Last reviewed: 2026-11-05

## Current status

- Phase 0A: **Approved for implementation by the Phase 0B request; product-spec validation remains pending.**
- Phase 0B: **Foundation implemented; automated tests pass.**
- Phase 0C: **Identity backend and initial React identity UI implemented; core backend tests pass. Frontend package installation and Playwright execution remain pending.**
- Phases 0D through 11: **Not started.**
- Application implementation: **Phase 0B foundation plus Phase 0C identity/authentication.** Business-domain workflows remain unimplemented.
- Source validation: **Pending** because `.github/copilot-instructions.md` and `docs/SPEC.md` are absent. Foundation follows the available approved design set and should be reconciled when those sources are restored.

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
