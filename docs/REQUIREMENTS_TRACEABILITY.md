# Requirements Traceability Matrix

All entries describe planned implementation and planned evidence; none are implemented or tested in the inspected workspace. Phase labels follow [IMPLEMENTATION_ROADMAP.md](IMPLEMENTATION_ROADMAP.md).

| Requirement | Module(s) | Phase | Planned verification |
|---|---|---:|---|
| Customers and suppliers | partners | 1 | Scoped CRUD integration tests; uniqueness/validation tests; API contract checks. |
| Orders and shipments | orders, shipments | 2 | Lifecycle transition tests, authorization tests, persistence integration tests, API contract tests. |
| Warehouse management | warehouse, inventory | 3 | Ledger/reservation invariants, concurrent allocation tests, task workflow integration tests. |
| Transportation management | transport | 4 | Assignment/dispatch lifecycle tests; driver scope tests; API contract checks. |
| Real-time GPS and control tower | tracking, notifications | 5 | Ingestion validation/dedupe/load tests; websocket authorization tests; stale position and retention tests. |
| Delivery exceptions | exceptions | 5, 8 | Transition/ownership tests, audit evidence tests, notification delivery/retry tests. |
| Driver management and mobile PWA | transport, frontend PWA | 4, 6 | Responsive workflow Playwright tests; offline queue/retry/conflict tests on supported browsers. |
| Proof of Delivery | proof, integrations/object storage | 6 | Upload authorization/content/size/checksum tests; signed URL expiry; acceptance and audit tests. |
| Inventory and stock movements | inventory, warehouse | 3 | Double-post prevention, transaction rollback, balance-vs-ledger reconciliation tests. |
| Financial management and invoicing | billing | 7 | Decimal/rounding and state tests; separation-of-duties authorization; immutable issued invoice tests. |
| Notifications and alerts | notifications | 8 | Outbox retry/idempotency tests; preference/localization tests; provider adapter contract tests. |
| Analytics and reporting | analytics | 9 | Aggregate correctness tests against seeded facts; access-scope tests; export limits tests. |
| Customer portal | frontend, identity, orders/shipments/billing | 10 | Tenant isolation tests and end-to-end customer journey tests. |
| Roles and permissions | identity | 0C, 10 | Permission matrix tests, negative object-scope tests, privilege escalation tests. |
| Audit logs | audit | 0C, 10 | Append-only behavior, redaction, actor/correlation metadata, access-control tests. |
| JWT access/refresh security | identity | 0C | Rotation/reuse/revocation integration tests; cookie/CSRF tests; token storage inspection. |
| Account protection/password hashing | identity | 0C | Hash configuration tests, rate/lockout tests, generic error behavior tests. |
| Secure file uploads | proof/integrations | 6, 11 | MIME sniffing, size, malware hook, object ownership and URL expiry tests. |
| Rate limiting and OWASP practices | shared/identity/API | 0C, 11 | Security test suite, rate-limit tests, dependency/image scans, penetration review. |
| English and Persian/Dari, RTL/LTR | frontend/i18n | 0D, 11 | Locale rendering/component tests, RTL visual checks, number/date formatting tests. |
| Jalali date presentation | frontend/i18n | 0D, 11 | Conversion boundary tests and locale snapshot/visual tests after library selection. |
| Docker, Compose, Nginx | infrastructure | 0E | Compose startup/health checks and reverse-proxy/API integration smoke tests. |
| GitHub Actions | infrastructure | 0B, 0E | CI pipeline on clean checkout, artifact build, migration and scan jobs. |
| Prometheus/Grafana/MailHog | infrastructure | 0E | Local service startup, scrape/dashboard check, test email flow. |
| Maintainability and forward-only schema evolution | all/backend | 0B onward | Clean build, migration upgrade test, architecture dependency checks and code review. |
