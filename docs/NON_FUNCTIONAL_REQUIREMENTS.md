# Non-Functional Requirements

**Status:** Initial proposed targets for review; these are planning targets, not measured properties. Confirm workload, user geography, and hosting before treating them as commitments.

| Area | Proposed target / design requirement |
|---|---|
| Availability | 99.5% monthly for core API at initial production stage; define maintenance exclusions and failure budget. |
| Latency | Release acceptance target: p95 < 300 ms for standard API endpoints under a documented test environment, excluding external provider time. This supersedes the earlier planning estimates of 500 ms for reads and 800 ms for commands. Control-tower updates should be visible within 10 seconds for connected clients. No Phase 11 measurements are available; the target is unverified. |
| Scale | Initial planning envelope: 100 concurrent web users, 500 active drivers, 10,000 shipments/day, 1 location update/driver/10 seconds while active. Load-test and revise before production. |
| Durability | No acknowledged order, inventory posting, invoice, audit, or outbox event may be lost; MySQL transactions and backup/restore are required. |
| Recovery | Initial proposed RPO ≤ 15 minutes and RTO ≤ 4 hours; validate infrastructure cost and restore drills. |
| Security | OWASP ASVS-aligned review; TLS in transit, encrypted backups/secrets, least privilege, secure headers, dependency/image scanning, audit of privileged actions. |
| Privacy | Minimize location and customer personal data; define consent, retention, access/export/deletion obligations per jurisdiction before launch. |
| Accessibility | Target WCAG 2.2 AA for critical workflows, keyboard operation, contrast, screen-reader labels. |
| Localization | English and Persian/Dari; RTL/LTR layouts, locale-aware number/date/currency presentation; persist canonical UTC instants and ISO currency codes. Jalali display is a presentation concern, with library and conversion behavior selected and verified. |
| Offline operation | Driver PWA can queue explicitly allowed actions and bounded task data; show sync state; server revalidates authorization/state; no guarantee of continuous GPS/background execution by mobile OS. |
| Observability | Structured logs, trace/correlation IDs, metrics for latency/errors/queue lag, dashboards and actionable alerts; no secrets, tokens, or unbounded labels. |
| Maintainability | Feature ownership, API contracts, ADRs, forward-only migrations, code review, automated unit/integration/contract/e2e coverage proportionate to risk. |
| Portability | Docker images and Compose for local; production platform remains to be selected. Avoid provider-specific assumptions outside adapters. |

Targets must be made measurable through agreed workload definitions, monitoring, and acceptance tests. High-volume GPS retention and map request volume are likely cost drivers.
