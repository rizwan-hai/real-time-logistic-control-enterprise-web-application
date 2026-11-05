# ADR 0011: Private Object Storage with Upload Quarantine

- Status: Proposed
- Date: 2026-11-05

## Context

POD media and documents need controlled access and cannot be trusted based on a client-provided filename or MIME type.

## Decision

Keep objects private. Issue narrow, short-lived upload intents after authorization, upload to quarantine, verify checksum/size/content and scan status, then mark accepted or reject. Store metadata and ownership in SQL; use a new object/version for replacements.

## Consequences

Unvalidated content is not exposed as accepted evidence. Object/SQL consistency, malware scanner, encryption, retention, and legal hold remain deployment concerns.
