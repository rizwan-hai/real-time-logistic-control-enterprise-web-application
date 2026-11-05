# ADR 0009: Idempotent Commands and Optimistic Concurrency

- Status: Accepted
- Date: 2026-11-05

## Context

Mobile retries, concurrent operators, and integration callbacks can duplicate or overwrite commands.

## Decision

Use durable idempotency records for retry-prone commands, request digests to detect key misuse, and aggregate versions/ETags for contested updates. Keep `/api/v1` compatible; introduce a new major version for incompatible changes.

## Consequences

Retries are safe and conflicts are visible. Idempotency retention, response replay policy, and API deprecation windows need operational definitions.
