# Implementation Roadmap

This roadmap preserves the requested phases and orders work by dependency. **Only Phase 0A planning is in scope now.** No phase is complete based on this document.

| Phase | Scope / exit evidence | Depends on |
|---|---|---|
| 0A | Architecture, ERD, API conventions, states, permissions, risks, traceability reviewed and approved. | Repository discovery (done: empty workspace) |
| 0B | Java 21/Spring Boot Maven modular foundation, config profiles, Flyway bootstrap, error handling, OpenAPI, health/metrics, coding rules; buildable CI. | 0A decisions; approved database and deployment assumptions |
| 0C | Identity, credentials, JWT access/refresh rotation, RBAC, scoped authorization, audit foundation, security tests. | 0B |
| 0D | React/TS strict foundation, routes/layout, API client, query/cache, i18n RTL/LTR shell, component tests. | 0A API and UX conventions; may proceed alongside 0B after 0A approval |
| 0E | Compose services, Nginx, CI, secrets pattern, metrics dashboards, local observability. | 0B/0D interfaces; can overlap foundation work |
| 1 | Organization/partner/catalog/warehouse master data. | 0B, 0C, 0D; permission model |
| 2 | Orders and shipment creation/lifecycle, events and customer snapshots. | 1 |
| 3 | Inventory ledger, reservations, warehouse receiving/pick/pack. | 1, 2 data contracts |
| 4 | Drivers/vehicles/trips/assignments and dispatch. | 1, 2; operational decision on routing |
| 5 | GPS ingestion, latest position, control tower, geofences, realtime notifications. | 2, 4, 0E; tracking privacy/retention decision |
| 6 | POD capture/upload, driver PWA offline queue, delivery confirmation. | 4, 5, object storage/security decisions |
| 7 | Charges, invoice approval/issue, payment reconciliation. | 1, 2, finance policy and jurisdiction decisions |
| 8 | Notification preferences/templates, event delivery, alerts. | 0E, outbox from 0B; feature events |
| 9 | Operational read models, analytics, exports and dashboards. | 2–8; data quality/retention decisions |
| 10 | Administration, audit UI, customer portal. | 0C, 1–9; customer visibility policy |
| 11 | Security hardening, performance/load/recovery tests, privacy review, integration and release readiness. | All implemented modules |

Build as thin vertical slices and keep migrations additive/forward-only. Do not advance automatically between phases: obtain user review at each phase boundary. Test evidence is defined per phase before claiming completion; production deployment is a separate approval and operational readiness step.
