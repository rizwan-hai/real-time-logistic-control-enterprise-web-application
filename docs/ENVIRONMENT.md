# Environment variables

Compose reads `.env` in the repository root. `.env.example` contains local-only placeholders and is not suitable for shared or production environments. Compose publishes service ports only on `127.0.0.1` by default.

| Variable | Purpose | Default / required |
| --- | --- | --- |
| `MYSQL_DATABASE` | MySQL database and Flyway target | `logistics` |
| `MYSQL_USER`, `MYSQL_PASSWORD` | Application DB user | `logistics`; password required |
| `MYSQL_ROOT_PASSWORD` | MySQL administrative initialization password | Required |
| `MYSQL_PORT` | Local MySQL host port | `3306` |
| `REDIS_PASSWORD` | Redis AUTH password | Required |
| `REDIS_PORT` | Local Redis host port | `6379` |
| `MINIO_ROOT_USER`, `MINIO_ROOT_PASSWORD` | S3-compatible object-store root credentials | Required; use URL-safe characters (letters, digits, `_`, `-`) because Compose passes them to the bucket initializer |
| `MINIO_API_PORT`, `MINIO_CONSOLE_PORT` | Local object API and console ports | `9000`, `9001` |
| `SMTP_PORT`, `MAILHOG_UI_PORT` | Local SMTP and MailHog UI ports | `1025`, `8025` |
| `APP_JWT_SECRET` | Signing key for access tokens | Required; use a random secret of at least 64 characters |
| `DEV_ADMIN_EMAIL`, `DEV_ADMIN_PASSWORD` | Development-only seeded administrator | `admin@example.test`; local placeholder |
| `SPRING_PROFILES_ACTIVE` | Spring profile | `dev` |
| `BACKEND_PORT`, `MANAGEMENT_PORT` | Local API and Actuator ports | `8080`, `9091` |
| `VITE_PORT`, `WEB_PORT` | Vite and Nginx frontend ports | `5173`, `80` |
| `PROMETHEUS_PORT`, `GRAFANA_PORT` | Observability UI ports | `9090`, `3000` |
| `GRAFANA_ADMIN_USER`, `GRAFANA_ADMIN_PASSWORD` | Local Grafana administrator | `admin`; password required |
| `API_SCHEMA_URL` | OpenAPI endpoint used at frontend image build | backend internal URL |

Spring-specific database, Redis, SMTP, MinIO, and JWT variables are mapped by Compose. Keep `.env` out of version control; never paste production values into CI YAML. CI uses ephemeral credentials or runner-provided services and read-only repository permissions where possible.
