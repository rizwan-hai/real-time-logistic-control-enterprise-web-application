# API Error Codes

**Status:** Proposed stable codes. Exact wording will be localized; the code and HTTP status are the contract.

| HTTP | Code | Meaning |
|---:|---|---|
| 400 | `VALIDATION_FAILED` | Request shape or field constraints failed. |
| 400 | `INVALID_FILTER` | Unsupported filter, sort, or pagination value. |
| 401 | `AUTHENTICATION_REQUIRED` | Missing/invalid access credential. |
| 401 | `CREDENTIALS_INVALID` | Login rejected; generic message to avoid account enumeration. |
| 401 | `REFRESH_TOKEN_INVALID` | Refresh token invalid, expired, or revoked. |
| 401 | `REFRESH_TOKEN_REUSE_DETECTED` | Previously rotated token reused; token family revoked. |
| 403 | `PERMISSION_DENIED` | Authenticated actor lacks permission. |
| 404 | `RESOURCE_NOT_FOUND` | Missing or out-of-scope resource; avoid cross-tenant disclosure. |
| 409 | `STATE_TRANSITION_INVALID` | Requested transition is not allowed. |
| 409 | `OPTIMISTIC_LOCK_CONFLICT` | Record changed since client read it. |
| 409 | `DUPLICATE_IDEMPOTENCY_KEY` | Key reused with a different request payload. |
| 409 | `BUSINESS_REFERENCE_CONFLICT` | Unique business reference already exists. |
| 413 | `UPLOAD_TOO_LARGE` | File exceeds configured size limit. |
| 415 | `UPLOAD_TYPE_UNSUPPORTED` | MIME/content type not allowed. |
| 422 | `INSUFFICIENT_STOCK` | Requested allocation exceeds available stock. |
| 422 | `SHIPMENT_STATE_CONFLICT` | Shipment command violates its lifecycle. |
| 422 | `POD_REQUIRED` | Required proof is missing for the command. |
| 429 | `RATE_LIMITED` | Request limit exceeded; may include `Retry-After`. |
| 503 | `DEPENDENCY_UNAVAILABLE` | Required external dependency temporarily unavailable. |
| 500 | `INTERNAL_ERROR` | Unexpected failure; response contains correlation ID only. |

Responses follow the envelope in [API.md](API.md). Never return stack traces, SQL, secrets, raw provider errors, or authorization internals. Log diagnostic details with redaction and correlation ID. Validation responses may include field path and safe localized message.
