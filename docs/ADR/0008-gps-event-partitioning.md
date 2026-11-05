# ADR 0008: Time-Partitioned GPS Event Store

- Status: Proposed
- Date: 2026-11-05

## Context

Location updates are high volume, time ordered, and have a shorter retention profile than shipment and finance records.

## Decision

Keep immutable tracking events separate from transactional aggregates; begin with monthly UTC `received_at` partitions after validating MySQL 8 constraints. Maintain a rebuildable latest-position projection. Retention drops eligible whole partitions only after legal-hold checks; compact dedupe state is retained for the retry window.

## Consequences

Retention and query pruning are operationally manageable. Partitioning affects keys, FKs, uniqueness, migrations, and restore procedures and must be benchmarked before schema commitment.
