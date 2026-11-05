# ADR 0004: Transactional Outbox for Domain Events

- Status: Accepted
- Date: 2026-11-04

## Context

Operational changes must produce reliable notifications and projections without dual-write loss between MySQL and external/event consumers.

## Decision

Persist versioned outbox records in the same transaction as business state. Publish asynchronously with retries; consumers deduplicate and are idempotent. WebSockets remain an ephemeral client notification channel.

## Consequences

Business state and event intent commit atomically. Outbox cleanup, lag monitoring, replay, and delivery operations must be implemented.
