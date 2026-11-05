# ADR 0003: UUID Public Identifiers

- Status: Accepted
- Date: 2026-11-04

## Context

External APIs serve multiple customer scopes and must not expose guessable sequential database keys.

## Decision

Use UUID public IDs for all externally addressable entities. Internal storage keys may differ, but are never API identifiers. Enforce tenant-scoped authorization independently of identifier unpredictability.

## Consequences

Harder enumeration and easier distributed ID generation, with larger indexes and no authorization substitute. UUID format/version and binary versus textual storage must be selected during Phase 0B.
