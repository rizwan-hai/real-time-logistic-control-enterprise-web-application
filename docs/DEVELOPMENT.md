# Development setup

## Prerequisites

- Docker Desktop with Compose v2.24 or later.
- Git.
- Java 21 and Maven 3.9+ for host-side backend work.
- Node.js 22 and npm for host-side frontend work.

## First run

From the repository root, create `.env` from `.env.example`, replace local placeholders, then start the development profile:

```powershell
Copy-Item .env.example .env
docker compose --profile dev up --build -d
docker compose ps
```

Compose waits for MySQL, Redis, and object storage readiness before starting the backend, then waits for API readiness before starting Vite. The backend uses dev settings, Flyway initializes/updates the schema, and the Vite app generates OpenAPI types from the running API.

Use `docker compose logs -f backend` for startup diagnostics. `docker compose down` preserves database, Redis, object-store, Prometheus, and Grafana volumes. `docker compose down -v` deletes that local state.

## Host-side development

You can run only infrastructure in Compose, then run backend/frontend locally:

```powershell
docker compose up -d mysql redis minio minio-bucket-init mailhog
mvn spring-boot:run
```

In another PowerShell window:

```powershell
Set-Location frontend
npm install
npm run dev
```

The local Vite server proxies `/api` to `http://localhost:8080`; set `VITE_API_PROXY_TARGET` to override it. Frontend API types are generated against `/v3/api-docs`.

## Production image preview

Set `.env` as above and run `docker compose --profile production up --build -d`. For an actual deployment, use a secrets manager, production profile, HTTPS at an ingress/load balancer, restricted service exposure, external durable storage, and an operational backup/restore process. Do not use the example credentials or dev profile in production.
