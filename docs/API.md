# API Contract Conventions

**Status:** Baseline contract conventions; most endpoints remain illustrative. Notifications, initial analytics/reporting, Phase 10 administration controls, and customer portal endpoints are implemented as described below.

## General

- Prefix `/api/v1`; JSON over HTTPS; UTF-8; UUID public IDs; UTC ISO-8601 instants.
- Use DTOs, Bean Validation, explicit response models, and OpenAPI. Do not serialize persistence entities.
- Collections use `page`, `size`, `sort` with bounded page size and stable ordering. Filters are allowlisted; tenant/customer scope is derived from authenticated identity.
- Mutating requests accept `Idempotency-Key` where retries could duplicate a business action. Require `If-Match`/version for conflict-prone edits or include an explicit version field.
- Correlation ID is accepted or generated and returned as `X-Correlation-ID`.
- WebSocket events hint clients to refresh authorized REST resources; notification messages carry only sanitized display text and links.

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
| Notifications | `/notifications`, `/notification-preferences` | Authenticated user and organization scoped. |
| Reports | `/reports/{dashboard,trends,deliveries,exports,schedules}` | Role-specific dashboards; tenant/customer/warehouse scoped reads; requester-scoped export jobs and schedules. |
| Customer portal | `/portal/{orders,shipments,addresses,profile,invoices,proofs}` | Customer role; organization and customer ID are derived from claims; cross-customer lookups return not found. |
| Administration | `/admin/{audit,settings,api-keys,webhooks,calendars,holidays,health}` | Organization administration; auditors have read-only audit access. |
| Integration clients | `X-API-Key` on `/integrations/orders` | Hashed key storage, expiry/revocation, and endpoint authority checks via named scopes such as `orders.read`. |
| Public tracking | `/tracking/public/{token}` and UI `/track/{token}` | High-entropy expiring token, rate-limited response with coarse recent coordinates and ETA only. |

### Phase 10 administration and customer portal

- `GET /admin/audit` supports actor, action, entity, security-event, timestamp, and bounded-result filters; `GET /admin/audit/export` returns CSV. Application APIs expose no audit update/delete operations. Audit snapshots strip password/token/secret and selected PII fields.
- `GET/PUT /admin/settings/{key}` supports `STRING`, `INTEGER`, `DECIMAL`, `BOOLEAN`, and `JSON`; secret values are encrypted at rest and omitted from read responses. Writes invalidate the in-process typed-setting cache.
- `POST /admin/sequences/{key}/next` issues organization-scoped sequence values. `PUT /admin/calendars/{key}` configures locale, IANA timezone and weekend days; `PUT /admin/holidays/{calendarKey}/{date}` manages Gregorian ISO dates.
- API keys are shown once at creation, stored as SHA-256 digests, limited by explicit authorities/scopes, and support expiry, revocation and last-use/audit records.
- Webhook secrets are encrypted and shown once at creation/rotation. Outbox events are deduplicated into delivery jobs, signed with HMAC-SHA256 (`X-Webhook-Signature`), attempted at most five times with exponential backoff, and expose delivery/attempt history. Redirects and non-public destination addresses are rejected.
- `GET /admin/health` reports database, Redis, MinIO, outbox/GPS metrics, and configured WebSocket/integration status.
- Customer order routes are `GET/POST /portal/orders`, `POST /portal/orders/bulk` (up to 100), and `GET /portal/shipments`. Customer addresses and locale are saved under the token's customer claim. Invoice/POD downloads require a registered clean attachment belonging to that customer and stream through the authenticated API.

Invoice issuance and billing persistence are not yet implemented in this repository; invoice documents must be registered by staff against a scanned attachment. The customer tracking page shows recent coarse coordinates and links to a map provider; it does not yet render an in-app live tile map. Jalali-calendar date conversion and automated outbox/webhook integration tests remain follow-up work.

### Reporting

The reporting API is implemented under `/api/v1/reports`. Dashboard, trend, and delivery-detail requests accept `from` and `to` ISO dates (maximum one year), with optional `customerId`, `warehouseId`, `vehicleId`, and `driverId` filters. Customer scope is taken from the authenticated token and cannot be widened by a supplied customer ID. Warehouse dashboards require an assigned warehouse scope.

- `GET /reports/dashboard` returns only the metric groups allowed for the caller's role.
- `GET /reports/trends` returns daily shipment totals grouped by status.
- `GET /reports/deliveries?page=0&size=100` is a bounded drill-down (size maximum 500).
- `POST /reports/exports?format=CSV|XLSX|PDF` queues an asynchronous delivery export; `GET /reports/exports` reports the actor's jobs and `GET /reports/exports/{id}/download` permits the requester or a caller granted `report.exports.download`.
- `GET/POST/DELETE /reports/schedules` manages the actor's daily, weekly, or monthly delivery export schedules.

Export rows are read with a forward-only cursor and written incrementally. XLSX uses Apache POI's streaming workbook. Generated files expire after seven days. KPIs whose source records are not persisted (including invoiced revenue, receivables, payables, inventory valuation/turnover, reorder thresholds, and availability calendars) are returned as unavailable rather than approximated. Profit/cost figures are grouped by currency and are never summed across currencies.

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

### Notifications

The notification API is implemented under `/api/v1` and always scopes reads and writes to the authenticated user and organization:

| Operation | Contract |
|---|---|
| `GET /notifications?page=0&size=20&unreadOnly=false` | Returns the user's notification history; page size is limited to 100. |
| `GET /notifications/unread-count` | Returns `{ "count": number }`. |
| `PATCH /notifications/{id}/read` | Marks one owned notification as read; another user's notification resolves to `404`. |
| `POST /notifications/read-all` | Marks all of the actor's unread notifications as read. |
| `GET /notifications/{id}/deliveries` | Returns channel, status, attempt count, and delivery timestamps without provider error details or recipient addresses. |
| `GET /notification-preferences` | Returns channel switches and selected `en`/`fa` locale. |
| `PUT /notification-preferences` | Accepts `{ "inApp": true, "email": false, "sms": false, "webPush": false, "locale": "en" }`. |

Business events are consumed from the transactional `outbox_event` table and deduplicated by event fingerprint. Notification records and channel delivery jobs are persisted before attempting delivery. Failed deliveries retry with exponential backoff and move to `DEAD_LETTER` after five attempts. WebSocket updates use the existing `/ws/tracking` STOMP endpoint and the private `/topic/notifications/{authenticated-user-id}` destination; subscribing to another user's destination is denied. Notification content uses plain-text English/Persian templates and excludes business payloads such as addresses, coordinates, amounts, and free-form event details.

The development profile includes local SMS and Web Push adapters that log acceptance rather than contacting an external provider. Production deployments must supply their own `NotificationChannelProvider` implementations for enabled SMS/Web Push channels; email uses the configured SMTP service.

## Offline synchronization contract

The driver client stores a bounded task snapshot and an append-only local command queue. Each command contains `commandId` (UUID), entity ID, expected entity version, command type, payload, client-created time and dependency IDs. Upload in order; server stores a durable idempotency result keyed by actor/device/command ID. Applied commands return canonical server state/version; duplicates return the original result; stale or invalid commands return a typed conflict requiring user resolution. The client never overwrites server state optimistically as final truth. Location batches can be accepted independently and deduplicated by device+batch/point ID. Offline credentials are not placed in browser storage beyond platform-protected session mechanisms, and queued data is encrypted where the platform supports it; logout/device revocation clears local credentials and sensitive cache.
