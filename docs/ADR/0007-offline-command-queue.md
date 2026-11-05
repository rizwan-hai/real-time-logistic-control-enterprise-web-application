# ADR 0007: Idempotent Offline Command Queue

- Status: Proposed
- Date: 2026-11-05

## Context

Driver connectivity is intermittent and retries can repeat commands after uncertain responses.

## Decision

Queue only explicitly supported commands on device with stable command UUIDs, expected server versions, dependency ordering, and bounded task snapshots. Server persists command outcomes and applies each idempotently after rechecking current authorization and business state. Location batches have separate dedupe keys. Conflicts require explicit resolution; local state never overrides canonical server state.

## Consequences

Drivers can continue bounded work offline and safely retry. Device storage protection, retention, supported browsers, and offline action scope must be confirmed.
