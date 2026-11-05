# ADR 0010: Immutable Issued Invoices and Append-Only Corrections

- Status: Accepted
- Date: 2026-11-05

## Context

Financial records require reproducible totals, auditability, and correction without erasing history.

## Decision

Use exact decimal amounts with explicit currency and approved rounding policy. Freeze invoice and line snapshots at issue. Correct issued documents only through linked credit/void/reissue records. Record payments append-only with unique external references and transactional balance checks.

## Consequences

Issued records remain auditable and reproducible. Jurisdictional tax, numbering, rounding, and payment rules must be decided before billing implementation.
