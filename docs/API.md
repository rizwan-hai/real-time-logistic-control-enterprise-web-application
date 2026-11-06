# API Contract Conventions

**Status:** Proposed contract conventions; endpoints are illustrative and unimplemented.

## General

- Prefix `/api/v1`; JSON over HTTPS; UTF-8; UUID public IDs; UTC ISO-8601 instants.
- Use DTOs, Bean Validation, explicit response models, and OpenAPI. Do not serialize persistence entities.
- Collections use `page`, `size`, `sort` with bounded page size and stable ordering. Filters are allowlisted; tenant/customer scope is derived from authenticated identity.
- Mutating requests accept `Idempotency-Key` where retries could duplicate a business action. Require `If-Match`/version for conflict-prone edits or include an explicit version field.
- Correlation ID is accepted or generated and returned as `X-Correlation-ID`.
- WebSocket events hint clients to refresh authorized REST resources; they do not carry sensitive data unnecessarily.

## Candidate resource map

| Resource/command | Candidate route | Access |
|---|---|---|
| Login/refresh/logout | `POST /auth/{login,refresh,logout}` | Public rate-limited login; refresh uses secure rotation; logout revokes session. |
| Customers/suppliers | `/customers`, `/suppliers` | Master-data permissions and organization scope. |
| Orders | `/orders`, `/orders/{id}`, `/orders/{id}/confirm` | Scoped read plus explicit create/confirm permissions. |
| Shipments | `/shipments`, `/shipments/{id}`, `/shipments/{id}/status` | Role and shipment/customer scope. |
| Inventory | `/warehouses/{id}/stock`, `/stock-movements`, `/reservations` | Warehouse scope and operation-specific permissions. |
| Transport | `/trips`, `/drivers`, `/vehicles`, `/assignments` | Transport permissions; driver sees own assigned work. |
| Tracking | `POST /tracking/positions`, `/tracking/vehicles/latest`, `/tracking/trips/{id}/{eta,history}`, `/tracking/geofences`, `/tracking/alerts`, `/tracking/shipments/{id}/public-link` | Device-token authenticated ingestion; tenant-scoped staff reads/actions; public tracking limited to unexpired high-entropy links. |
| Exceptions/POD | `/exceptions`, `/shipments/{id}/pod/upload-url`, `/shipments/{id}/pod` | Scoped permission; short-lived signed upload/download URLs. |
| Transport | `/transport/trips`, `/transport/trips/{id}/dispatch`, `/transport/trips/{id}/commands/{command}` | Trip lifecycle, driver/vehicle/shipment assignment, active-resource conflict checks, odometer, fuel, expense and status history. |
| Routing | `POST /transport/route-preview` | Heuristic nearest-neighbor plus 2-opt ordering and estimated Haversine distance; response identifies heuristic output and is not globally optimal. |
| Operational validation | `POST /transport/validate` | Checks supplied load, vehicle, driver, availability, license, driving-hour, and working-hour snapshots. |
| Carrier tenders | `/transport/tenders`, `/transport/tenders/{id}/quote`, `/transport/tenders/{id}/award` | Invite carriers, record quotes, and award one quoted tender per shipment. |
| Billing | `/invoices`, `/invoices/{id}`, `/invoices/{id}/issue` | Finance permissions; issued documents immutable. |
| Notifications | `/notifications`, `/notification-preferences` | Actor scoped. |

## Error envelope

```json
{
  "timestamp": "2026-11-04T12:00:00Z",
  "status": 409,
  "code": "SHIPMENT_STATE_CONFLICT",
  "message": "The shipment cannot be dispatched from its current state.",
  "fieldErrors": [],
  "correlationId": "8c3d..."
}
```

Messages are localized where appropriate; `code` is stable and clients should branch on it. Never reveal existence of another tenant's resource. See [ERROR_CODES.md](ERROR_CODES.md).

## Security and contract evolution

Bearer access token for REST. Refresh token must be delivered in a Secure, HttpOnly, SameSite cookie for browser clients (CSRF controls required) or protected platform storage for driver clients; never localStorage. Define allowed CORS origins. Validate object access in service layer. Breaking changes require `/v2` or an explicitly compatible migration window. Contract tests and generated OpenAPI diff checks are planned for Phase 0B onward.

## Command and response contracts (baseline)

All commands return a resource representation or `202 Accepted` only when work is genuinely asynchronous. Create returns `201 Created` with `Location`; reads return `200`; deletes/cancellations return `204` or the updated resource. Commands include `Idempotency-Key` for client retries and `If-Match` (ETag) for version-sensitive updates. Stable pagination is cursor-based for event feeds and may be page/size for bounded master-data lists; maximum page size is 100. Monetary/quantity fields are decimal strings in JSON to prevent binary floating-point loss. Dates use RFC 3339 UTC instants; business-local dates include an explicit local date and zone.

| Operation | Request essentials | Success / key errors |
|---|---|---|
| `POST /api/v1/auth/login` | login, password, optional device label | `200` tokens/session; `401 CREDENTIALS_INVALID`, `429 RATE_LIMITED` |
| `POST /api/v1/auth/refresh` | HttpOnly cookie or protected client token | `200` rotated token; `401 REFRESH_TOKEN_INVALID/REUSE_DETECTED` |
| `POST /api/v1/orders` | customer ID, lines, ship-to snapshot, requested date, currency | `201`; validation/reference conflict |
| `POST /api/v1/orders/{id}/commands/confirm` | version, optional reason | `200`; `409 STATE_TRANSITION_INVALID/OPTIMISTIC_LOCK_CONFLICT` |
| `POST /api/v1/shipments/{id}/commands/dispatch` | trip/assignment reference, version | `200`; `409`, `422 INSUFFICIENT_STOCK` or `POD_REQUIRED` as policy applies |
| `POST /api/v1/driver/location-batches` | batch ID, device time, points with lat/lon/accuracy/observedAt | `202` accepted count/rejected point indexes; `400`, `413`, `429` |
| `GET /api/v1/shipments/{id}/tracking` | cursor, from/to | `200` authorized event page; out-of-scope resolves to `404` |
| `POST /api/v1/tracking/positions` | `X-Device-Id`, `X-Device-Token`, up to 500 event positions | `202` queued batch; device credentials and per-request rate limits apply |
| `GET /api/v1/tracking/trips/{id}/history` | `from`, `to` (maximum seven days) | `200` bounded replay positions; organization-scoped trip |
| `POST /api/v1/shipments/{id}/pod/upload-intents` | content length, declared MIME, checksum, capture metadata | `201` short-lived upload intent; `413`, `415`, `403` |
| `POST /api/v1/pod/{id}/complete` | object key token, observed checksum | `202` scanning/validation; `409` invalid state, `422` checksum/type failure |
| `POST /api/v1/invoices/{id}/commands/issue` | expected version, approval reference | `200` immutable invoice; `409`, `422` policy/separation-of-duties failure |
| `POST /api/v1/offline/sync` | device ID and ordered commands with stable command IDs | per-command applied/rejected/conflict result; retry is idempotent |

The precise field schemas, enums, limits, and role scopes are generated in OpenAPI during foundation work after the missing product spec is supplied. Signed object URLs are bearer capabilities: short TTL, one object and operation, no list permission; complete/scan/accept remains a server-side workflow.

## Offline synchronization contract

The driver client stores a bounded task snapshot and an append-only local command queue. Each command contains `commandId` (UUID), entity ID, expected entity version, command type, payload, client-created time and dependency IDs. Upload in order; server stores a durable idempotency result keyed by actor/device/command ID. Applied commands return canonical server state/version; duplicates return the original result; stale or invalid commands return a typed conflict requiring user resolution. The client never overwrites server state optimistically as final truth. Location batches can be accepted independently and deduplicated by device+batch/point ID. Offline credentials are not placed in browser storage beyond platform-protected session mechanisms, and queued data is encrypted where the platform supports it; logout/device revocation clears local credentials and sensitive cache.
