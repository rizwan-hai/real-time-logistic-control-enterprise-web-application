# Database initialization

MySQL 8.4 initializes the `logistics` database and application account from `MYSQL_DATABASE`, `MYSQL_USER`, and their passwords when its data volume is empty. After MySQL passes its health check, Spring Boot starts and Flyway applies the versioned migrations under `src/main/resources/db/migration`.

The `mysql-data` named volume persists across container restarts and `docker compose down`. Changing initialization variables after the volume already exists does not recreate the database users or database. To reset a disposable local database, stop the stack and remove volumes with `docker compose down -v`, then start it again. This permanently deletes local DB and all other Compose volumes.

Check readiness with `docker compose exec mysql healthcheck.sh --connect --innodb_initialized` and inspect applied migrations in `flyway_schema_history`. The container root account is reserved for administrative maintenance; the application connects as `MYSQL_USER`.
