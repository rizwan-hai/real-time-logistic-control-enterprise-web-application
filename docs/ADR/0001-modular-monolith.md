# ADR 0001: Modular Monolith

- Status: Proposed
- Date: 2026-11-04

## Context

The product spans tightly related workflows and is at project inception. Independent services would add deployment, observability, and data consistency costs before team boundaries or scale are known.

## Decision

Start with a Spring Boot modular monolith organized by feature. Enforce module ownership, avoid cross-module JPA entity access, and use explicit application interfaces and domain events. Extract services only when operational or team evidence justifies it.

## Consequences

Simpler transactions and local development; one deployable and potential shared-database coupling. Module boundaries and architecture checks are essential.
