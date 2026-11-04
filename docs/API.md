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
| Tracking | `POST /driver/location-batches`, `GET /shipments/{id}/tracking` | Device/user authenticated; ownership checked; rate and payload bounds. |
| Exceptions/POD | `/exceptions`, `/shipments/{id}/pod/upload-url`, `/shipments/{id}/pod` | Scoped permission; short-lived signed upload/download URLs. |
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
