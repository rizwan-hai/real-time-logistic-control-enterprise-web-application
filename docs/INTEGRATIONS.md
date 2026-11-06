# Integration Architecture

**Status:** Proposed integration boundaries. No external providers are configured.

Routing adapters are implemented behind `RoutingProvider`. Select `haversine` (default), `osrm`, or `google-directions` with `TRANSPORT_ROUTING_PROVIDER`; configure `OSRM_BASE_URL` or `GOOGLE_DIRECTIONS_API_KEY` for the corresponding service. Haversine produces straight-line estimates. Nearest-neighbor plus 2-opt remains heuristic, including when a road-routing provider supplies distances.

| Integration | Direction/data | Design and controls | Open item |
|---|---|---|---|
| Maps/geocoding | Address normalization, route display, tiles | Provider adapter; cache only per license; provide attribution; never make provider outage block order persistence. Leaflet is renderer, not map data source. | Choose provider, licensing, offline needs, routing provider and budget. |
| Carrier/3PL | Shipment tender, labels, milestones, tracking | Per-carrier adapter, credentials in secret store, signed/authenticated webhooks, schema validation, dedupe by provider event ID, retries via outbox, dead-letter/reconciliation view. | Identify carriers, API contracts, direction, SLA. |
| GPS/device | Driver app location batches | Authenticated device/user, consent, timestamp/accuracy validation, rate/size limits, stale-event handling, dedupe, sampling policy. | Required update frequency, privacy/retention, background OS constraints. |
| MinIO/object storage | POD photos, signatures, documents | Private buckets, short-lived pre-signed URLs, server-side MIME/size/checksum validation, malware scan hook, encryption, lifecycle policy. | Retention, regional residency, malware scanning choice. |
| Email | Alerts, invitations, receipts | SMTP adapter; MailHog for local; templates outside business logic; retry/outbox; suppress sensitive content. | Production provider, sender domain, deliverability policy. |
| Redis | Cache, rate limits, transient coordination | No source-of-truth data; bounded TTL, eviction, authentication/network isolation. | Production topology and HA. |
| Prometheus/Grafana | Metrics and operational dashboards | Scrape private Actuator endpoints; avoid high-cardinality labels/PII; alert on SLO and queue lag. | Hosting, retention, alert destination. |
| Tax/payment (optional) | Tax calculations and payment reconciliation | Port/adapter, idempotent provider references, signed webhooks, immutable financial ledger, reconciliation. | Jurisdictions/providers and whether in scope. |

External calls must have timeouts, bounded retries with jitter, circuit-breaking where appropriate, correlation IDs, and explicit failure handling. Webhooks require signature verification, replay protection, payload size limits, and audit trails. Credentials rotate and are never committed. Define a provider-independent contract and sandbox fixtures before enabling each adapter.
# Routing providers

Set `TRANSPORT_ROUTING_PROVIDER` to `haversine` (default), `osrm`, or `google-directions`. Configure `OSRM_BASE_URL` for an OSRM-compatible route service or `GOOGLE_DIRECTIONS_API_KEY` for Google Directions. Haversine returns straight-line estimates; OSRM and Google are live road routes. Route stop ordering remains heuristic even with a road-routing provider.
