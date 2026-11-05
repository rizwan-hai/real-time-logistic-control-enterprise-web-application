# Logistics Control Backend

Java 21 / Spring Boot 3 modular monolith foundation. Domain feature packages own their API, application, domain, and infrastructure layers. Identity schema changes are reserved for Phase 0C.

## Run locally

1. Start infrastructure: `docker compose up -d mysql redis minio`.
2. Start the API: `mvn spring-boot:run` (default profile: `dev`).
3. Check `http://localhost:8080/api/v1/system/status`, Swagger at `http://localhost:8080/swagger-ui.html`, and health/readiness on management port `9091`.

Set `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `REDIS_HOST`, `REDIS_PORT`, `MINIO_ENDPOINT`, `MINIO_ACCESS_KEY`, `MINIO_SECRET_KEY`, and `MINIO_BUCKET` to override local defaults. Select `staging` or `prod` with `SPRING_PROFILES_ACTIVE`; those profiles require credentials through environment/secret injection.

`mvn test` runs context, migration, API error, correlation, pagination, and external-client configuration checks. The tests use H2 and do not require running MySQL, Redis, MinIO, or Docker. Production uses MySQL 8, Redis, and MinIO-compatible object storage; `ddl-auto` is always `validate` and schema changes use Flyway.
