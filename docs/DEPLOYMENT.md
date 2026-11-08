# Deployment and release operations

## Status

The repository includes Dockerfiles and a Docker Compose development/preview topology. **A Docker build, Compose startup, health/readiness check, and production deployment were not run during Phase 11** because Docker is unavailable in the validation environment. Treat this as a deployment foundation, not a verified production deployment.

## Local Compose

1. Install Docker Desktop with Compose v2.24 or later.
2. Copy `.env.example` to `.env`; replace every local placeholder with unique local-only values.
3. Start the development stack:

   ```powershell
   docker compose --profile dev up --build -d
   docker compose ps
   docker compose logs -f backend
   ```

4. Confirm service health and test the frontend, API, Swagger UI, and required integrations. Stop with `docker compose down`; `docker compose down -v` deletes local data volumes.

For the Nginx frontend preview, build/run the `production` Compose profile after setting its API schema build argument. This profile does not by itself provide production TLS, secret management, high availability, external durable storage, or a tested recovery process.

## Production deployment gate

Before deploying, configure a production Spring profile, managed secret injection, TLS at a controlled ingress, restrictive CORS and browser security headers, private durable object storage, database and object-store backups, alerting, least-privilege service credentials, and tested recovery procedures. Do not expose development credentials, MailHog, storage consoles, or management endpoints to public networks.

Deploy only immutable, scanned images after CI passes. The current repository does not have evidence of a passing CI run, a container vulnerability scan, a production-like MySQL migration, or a tested production deployment. The API p95 <300 ms target and all critical workflows remain unverified; release is not recommended.

## Runtime and operations

Use `/actuator/health/readiness` for readiness only from trusted infrastructure and verify that the management port is not publicly reachable. Review structured logs and Prometheus/Grafana telemetry, alert on database/Redis/object-store health and outbox/job/GPS backlog, and define ownership/escalation procedures before production. Exercise provider outage and reconnect behavior in a deployment-like environment.

