# Logistics Control

Spring Boot 3 / Java 21 backend and React 18 / TypeScript frontend for a logistics control platform. The repository uses a modular monolith, MySQL as the system of record, Redis, and private S3-compatible object storage.

## Start locally

Requirements: Docker Desktop with Compose v2.24+, Git, and (for host frontend commands) Node.js 22+. Docker Compose runs MySQL, Redis, Silo (MinIO-compatible object storage), MailHog, Prometheus, Grafana, and the backend. The `dev` profile adds Vite.

```powershell
Copy-Item .env.example .env
# Edit .env and replace every local placeholder password/secret.
docker compose --profile dev up --build -d
```

Open the frontend at <http://localhost:5173>, API at <http://localhost:8080>, Swagger at <http://localhost:8080/swagger-ui/index.html>, MailHog at <http://localhost:8025>, MinIO console at <http://localhost:9001>, Prometheus at <http://localhost:9090>, and Grafana at <http://localhost:3000>. The local development admin credentials are controlled by `DEV_ADMIN_EMAIL` and `DEV_ADMIN_PASSWORD` in `.env`.

Check service state with `docker compose ps`; follow application logs with `docker compose logs -f backend frontend-dev`. Flyway applies schema migrations automatically on backend startup.

To stop containers while preserving data, run `docker compose down`. To erase all local data volumes, run `docker compose down -v`.

## Production-style frontend preview

Build and run the Nginx frontend profile after the backend is healthy:

```powershell
docker compose --profile production up --build -d
```

It serves the frontend at <http://localhost/> and proxies API, OpenAPI, and Swagger requests to the backend. This Compose configuration is a deployment foundation; production secrets, TLS termination, external managed services, backups, and image publishing must be configured for the target environment.

## Development and operations guides

- [Development setup](docs/DEVELOPMENT.md)
- [Environment variables](docs/ENVIRONMENT.md)
- [Database initialization](docs/DATABASE.md)
- [Backup and restore](docs/BACKUP_RESTORE.md)
- [Architecture and implementation status](docs/PROGRESS.md)

CI workflows are in `.github/workflows/`. They run Maven verification, frontend lint/tests/build, dependency review, CodeQL, npm audit, and container image builds. No credentials are stored in workflow files.
