# Security assessment and controls

## Status

The Phase 11 security assessment is **incomplete**; this document is not a security certification or a statement that the application is production-ready. No dependency or container scan was executed in the local environment. A scanner configured in CI is not evidence that a scan passed.

The CI workflow configures CodeQL for Java and JavaScript/TypeScript, `npm audit`, and GitHub dependency review on pull requests. These jobs were not run as part of this validation. There is no configured container image vulnerability scan in the workflow. The required product specification and `.github/copilot-instructions.md` were not present, so security requirements could not be reconciled against them.

The project documentation and implementation describe JWT authentication with rotating refresh tokens, CSRF protection, role and object-scope checks, hashed API keys, signed webhooks, private object storage, audit logging, and rate limiting. These controls still require end-to-end verification against deployed configuration and abuse cases. Do not treat this inventory as confirmation that every control is effective.

## Review coverage required before release

| Area | Evidence required |
|---|---|
| Authorization and data scope | Negative API tests for cross-customer orders, other drivers' trips, unassigned warehouses, restricted finance data, and administrative actions. Verify checks at the server/use-case boundary. |
| Authentication and sessions | Verify token claims, expiry, logout/revocation, concurrent refresh rotation, reuse detection, lockout, password reset, and MFA behavior. |
| CSRF, CORS, and browser protections | Verify cookie-authenticated mutations reject missing/invalid CSRF tokens; inspect production CORS allowlists, CSP (including `frame-ancestors`), HSTS, `X-Content-Type-Options`, and `Referrer-Policy` at the TLS-terminating ingress. |
| Injection and SSRF | Review all query construction and outbound URL handling; test webhook destination validation, DNS resolution/rebinding, redirects, and private/link-local address restrictions. |
| Uploads and object permissions | Verify quarantine, content/size limits, malware scanning, private bucket policy, ownership checks, and safe download behavior. |
| Secrets, cryptography, and audit | Verify production secret injection, key lifecycle, encrypted storage/backups, adaptive password hashes, sensitive-log redaction, audit completeness, and restricted log access. |
| Dependencies and images | Run dependency review, CodeQL, npm audit, Maven dependency vulnerability scanning, and a container scanner against the exact release artifacts; triage and record results. |
| Reliability and abuse resistance | Exercise rate limits, webhook/API-key revocation, provider failures, replay/idempotency, and security-event monitoring under deployment-like configuration. |

Security headers, TLS, CORS, secrets, and backup encryption depend partly on deployment infrastructure; validate the effective response and ingress configuration, not only application source.

## Findings and follow-up

The bounded source review confirmed two medium-severity authorization findings. Both were fixed in this working tree and have focused regression tests; this does not complete the broader OWASP assessment.

| Severity | Location | Finding and remediation | Retest |
|---|---|---|---|
| Medium | `src/main/java/com/example/logistics/warehouse/application/WarehouseService.java:67-69` | Warehouse allocation accepted an order UUID without checking organization ownership, exposing order-line product/quantity data and allowing a foreign-order pick list. Allocation now locks and validates the organization-scoped order first, returns not-found for inaccessible orders, and scopes line selection through the validated organization. | `WarehouseServiceOrganizationScopeTest` rejects a foreign order before subsequent database operations. |
| Medium | `src/main/java/com/example/logistics/tracking/TrackingWebSocketConfiguration.java:37,56-61` | A WebSocket could retain an authenticated principal after its access token expired or its device-session family was revoked. Inbound commands and authenticated outbound frames now validate token expiry and the persisted, non-revoked family via `TrackingWebSocketSessionValidator`. | `TrackingWebSocketSessionValidatorTest` covers active, expired, and revoked/missing sessions. A live WebSocket logout/revocation test remains outstanding. |

`mvn clean verify` passed after these changes: 57 tests, 0 failures, 0 errors, 0 skipped. The outbound-interceptor behavior was not verified against a live broker. Dependency/image scanning and the remaining OWASP categories listed above are still unverified; do not interpret this bounded review or passing tests as a complete audit or a zero-vulnerability result.
