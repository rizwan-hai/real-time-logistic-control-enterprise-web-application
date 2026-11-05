# ADR 0012: Deny-by-Default Scoped Authorization and Audit

- Status: Accepted
- Date: 2026-11-05

## Context

The platform serves organization staff, customers, drivers, and administrators with overlapping IDs but different data visibility.

## Decision

Combine explicit permission grants with server-derived tenant, customer, warehouse, region, or assignment scope on every data access. Deny by default, return indistinguishable not-found results for out-of-scope resources, and append redacted audit events for privileged and business-critical actions.

## Consequences

Authorization is testable and tenant escape risk is reduced. Role catalog, break-glass access, audit retention, and legal access review need approval.
