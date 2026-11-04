# Open Questions and Risks

**Baseline:** Workspace inspection on 2026-11-04 found only `.git`, no commits, no source, migrations, configuration, or existing documentation. Repository remote is named in the pasted prompt, but no remote is configured or fetched in this workspace. All designs below are proposals.

## Decisions needed before foundation

1. **Organization/tenant model:** Is this one operator with separate customer accounts, or a multi-organization SaaS platform? This changes nearly every ownership key, uniqueness rule, role, and query policy.
2. **Deployment/data residency:** Hosting environment, geography, data residency constraints, and expected availability/cost envelope are unknown.
3. **Workload:** Confirm users, drivers, shipments/day, location cadence, peak event burst, history retention, and map traffic; current NFR figures are planning assumptions only.
4. **Operational geography:** Countries, time zones, languages, currencies, tax rules, Jalali requirements, address formats, and legal POD/invoice retention are unspecified.
5. **WMS depth:** Are lots, serial numbers, expiration, multiple units of measure, cross-docking, returns, and cycle counts required in first release?
6. **Tracking/privacy:** GPS consent, background sampling, visibility, geofence use, retention/deletion, and driver device ownership need policy decisions.
7. **Identity:** Login identifier, MFA requirement, SSO/OIDC, account invitation, password policy, lockout thresholds, and refresh-cookie/client behavior need approval.
8. **Roles and separation of duties:** Confirm role catalog, customer data boundaries, invoice approval thresholds, emergency access, and audit retention.
9. **Integrations:** Name carriers, map/geocoding/routing provider, email provider, optional payment/tax providers, and their API/SLA/authentication constraints.
10. **POD/file policy:** Allowed content, maximum sizes, malware scanning, retention, encryption, signatures/consent, and deletion policy.
11. **Financial policy:** Currency precision, tax jurisdiction, invoice numbering, rounding, credit/void treatment, payment reconciliation and audit rules.
12. **Mobile offline expectations:** Supported OS/browser/device fleet, offline duration, location capability, and acceptable sync conflict behavior.
13. **Production operations:** Choose deployment platform, secrets manager, HA topology, backup ownership, alert destination, and RPO/RTO budget.

## Material risks and mitigations

| Risk | Impact | Mitigation / gate |
|---|---|---|
| Empty repository and no remote checkout | Cannot assess an upstream implementation or preserve existing behavior; design may diverge from intended repo. | Confirm repository source/remote and fetch or provide files before Phase 0B; inspect real branch/history then. |
| Scope is very broad for one delivery | Inconsistent modules and unfinished workflows. | Vertical slices, phase gates, explicit MVP and acceptance criteria. |
| Cross-feature database coupling in modular monolith | Refactors become risky and boundaries erode. | Feature-owned entities/repositories, interface-based application calls, architecture checks, domain events/outbox. |
| GPS event volume and spatial query cost | Storage, index growth, slow control-tower views, cost. | Measure real cadence; latest-position projection, retention/aggregation, partitioning only after volume validation, spatial query plans. |
| Offline driver commands race with server state | Duplicate or invalid dispatch/delivery actions. | Queue with idempotency keys, server revalidation, explicit conflict UX, audit and sync status. |
| Refresh token theft/reuse | Account takeover. | Hash at rest, single-use rotation, family revocation, secure cookie/CSRF controls, anomaly audit and tests. |
| Tenant/object authorization omission | Cross-customer data disclosure. | Scope every repository query and websocket subscription; negative authorization tests; deny by default. |
| PII/location/POD exposure | Privacy/legal and security harm. | Data minimization, signed short-lived URLs, encryption, malware scans, retention policy, access audit. |
| Event delivery duplicate/loss | Missed alerts or incorrect downstream projection. | Transactional outbox, at-least-once delivery, idempotent consumers, replay/reconciliation tooling. |
| MySQL spatial and time-series assumptions | Index behavior or growth differs from plan. | Confirm exact MySQL version/features and benchmark representative queries before schema lock. |
| Stack version compatibility/drift | Upgrade and dependency conflicts across Spring, Hibernate Spatial, and MySQL. | Pin supported versions in BOM/CI and verify compatibility in Phase 0B; no replacement proposed. |
| Unconfirmed financial/legal rules | Incorrect invoices and records. | Keep financial workflows gated until jurisdiction, numbering, rounding, taxes, and correction policies are approved. |

## Recommendation

Before Phase 0B, confirm that this workspace is intended to begin empty or provide the actual repository checkout, then resolve questions 1–4, 7–10, and 13 at least at a provisional level. Approve an MVP boundary and this architecture. Phase 0B can then establish the build, module skeleton, Flyway baseline, API/error conventions, and operational profiles without locking unresolved business rules into schema.
