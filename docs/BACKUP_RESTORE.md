# Database backup and restore

See [BACKUP_AND_RECOVERY.md](BACKUP_AND_RECOVERY.md) for recovery targets, object storage/configuration gaps, and the Phase 11 test status. No backup or restore drill has been completed for this release validation.

Backups are logical SQL dumps of the configured application database. Protect backup files like production data and store them off-host for disaster recovery.

PowerShell backup:

```powershell
./scripts/backup-db.ps1
```

PowerShell restore (destructive to the selected database contents):

```powershell
./scripts/restore-db.ps1 -InputFile .\backups\logistics-YYYYMMDD-HHMMSS.sql
```

The scripts require a running Compose MySQL service and configured `.env`. Validate backup files and periodically test restore into an isolated database. Object attachments and database dumps have separate lifecycles; back up the `minio-data` volume or its production equivalent separately. For a consistent full recovery point, stop writes or coordinate database and object storage snapshots.
