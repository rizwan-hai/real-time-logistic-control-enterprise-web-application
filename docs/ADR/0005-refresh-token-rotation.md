# ADR 0005: Rotating Refresh Token Families

- Status: Proposed
- Date: 2026-11-04

## Context

Long-lived credentials create account takeover risk; logout and stolen-token detection must work across application instances.

## Decision

Issue short-lived JWT access tokens and opaque single-use refresh tokens. Store refresh token hashes and family/session metadata; atomically rotate, revoke on reuse, and support device/session logout. Browser delivery uses Secure, HttpOnly, SameSite cookies with CSRF protection.

## Consequences

Compromise detection and server-side revocation are possible. Rotation races, multi-tab behavior, CSRF, and native client storage need explicit handling and tests.
