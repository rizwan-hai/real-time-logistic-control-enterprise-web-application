# Logistics Control Backend

Java 21 / Spring Boot 3 modular monolith with identity, authentication, and authorization foundation. Domain feature packages own their API, application, domain, and infrastructure layers.

## Run locally

1. Start infrastructure: `docker compose up -d mysql redis minio mailhog`.
2. Start the API: `mvn spring-boot:run` (default profile: `dev`). The dev-only seeded account defaults to `admin@logistics.local` / `LocalAdmin123!ChangeMe`; set `DEV_ADMIN_EMAIL` and `DEV_ADMIN_PASSWORD` before starting to override it.
3. Start the identity UI with `cd frontend`, `npm install`, `npm run dev`. Open `http://localhost:5173`; Vite proxies `/api` calls to the API on port `8080`. Set `VITE_API_BASE` to the API origin for a production UI build. MailHog captures password reset messages at `http://localhost:8025`.
4. Swagger is at `http://localhost:8080/swagger-ui.html`; health/readiness are on management port `9091`.

Set `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `REDIS_HOST`, `REDIS_PORT`, `MINIO_ENDPOINT`, `MINIO_ACCESS_KEY`, `MINIO_SECRET_KEY`, and `MINIO_BUCKET` to override local defaults. Select `staging` or `prod` with `SPRING_PROFILES_ACTIVE`; those profiles require credentials through environment/secret injection.

`mvn test` runs context, identity migration, API/CSRF, authentication, password reset, RBAC data-scope, and TOTP checks. The tests use H2 and do not require running MySQL, Redis, MinIO, or Docker. Run UI checks with `cd frontend`, `npm install`, `npm run build`, and `npm run test:e2e`. Production uses MySQL 8, Redis, SMTP, and MinIO-compatible object storage; `ddl-auto` is always `validate` and schema changes use Flyway.
