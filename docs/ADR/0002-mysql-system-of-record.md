# ADR 0002: MySQL as Transactional System of Record

- Status: Proposed
- Date: 2026-11-04

## Context

The required stack specifies MySQL 8, Hibernate Spatial, and Flyway. Orders, stock, shipment, and finance commands need transactional integrity.

## Decision

Use MySQL 8/InnoDB as the authoritative transactional store; spatial types/indexes for location use cases after version-specific validation. Redis is non-authoritative. Use Flyway forward-only migrations.

## Consequences

Consistent transactions and required stack alignment. GPS volume and spatial query costs need benchmarking; retention and partitioning remain to be designed from actual workload.
