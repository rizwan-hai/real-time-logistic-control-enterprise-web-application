# ADR 0006: Object Storage for POD Documents

- Status: Proposed
- Date: 2026-11-04

## Context

Photos, signatures, and documents are large binary data; database rows should not carry their payloads.

## Decision

Store binaries in private MinIO buckets and metadata/ownership/checksum in MySQL. Issue short-lived scoped upload/download URLs only after permission checks; validate uploaded content and apply retention policy.

## Consequences

Database remains lean and object lifecycle can be managed. Object-store/SQL consistency, malware scanning, backup, residency, and retention need operational design.
