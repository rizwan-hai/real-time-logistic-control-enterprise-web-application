# Backup and recovery

## Status and targets

Recovery has **not been tested** in this environment. The proposed targets in [NON_FUNCTIONAL_REQUIREMENTS.md](NON_FUNCTIONAL_REQUIREMENTS.md) are RPO ≤15 minutes and RTO ≤4 hours; they are planning targets, not verified capabilities or measured results.

## Current repository tools

- `scripts/backup-db.ps1` runs a logical `mysqldump` against the Compose MySQL service.
- `scripts/restore-db.ps1` imports a selected SQL dump into the configured database and can overwrite its contents.
- MySQL and object data use Compose volumes. No automated off-host backup, MinIO snapshot/replication job, configuration backup process, or retention enforcement was verified.

The scripts require Docker Compose, a running database, and a correctly configured `.env`. They were not executed as part of Phase 11. Protect dumps and object data as sensitive production data; encrypt them in transit and at rest and restrict access.

## Recovery procedure

1. Declare the incident, identify the target environment and recovery point, and stop or isolate writes as required.
2. Provision an isolated compatible database and object-storage target; validate backup provenance and integrity.
3. Restore the database with `scripts/restore-db.ps1 -InputFile <verified-dump>` only after confirming the Compose environment and target. The script replaces data in the selected database.
4. Restore the matching object-store data using the configured Silo/MinIO-compatible provider's tested procedure. Keep database metadata and object versions consistent; the repository does not currently automate this step.
5. Restore deployment configuration and secrets from the approved secret/configuration backup process; never place production secrets in this repository or an unprotected dump.
6. Apply/verify migrations, check referential integrity and representative business records, then perform application readiness and authorization smoke tests.
7. Resume writes only after business owners approve validation; record actual recovery time, recovered point, data gaps, and corrective actions.

Test a full database-plus-object restore in an isolated environment, verify retention and off-host copies, and measure actual RPO/RTO before declaring the recovery objective met.

