# Local development

## Prerequisites

| Tool | Version | Needed for |
| --- | --- | --- |
| Docker with Compose v2 | recent | running everything, backend integration tests |
| Java (Temurin or any JDK) | 21 | backend development |
| Maven | 3.9+ | backend build (no wrapper is committed yet) |
| Node.js | 22.22.3+ or 24.15+ | frontend development (Angular 22) |

## Run the whole stack

```bash
docker compose up --build
```

Open <http://localhost:8080>. The footer says whether the API is reachable.
No `.env` file is needed; copy `.env.example` to `.env` to change ports or
credentials. The database listens on `localhost:5432` (loopback only).

Stop with `Ctrl+C`; `docker compose down -v` also deletes the database volume.

## Work on the backend

```bash
cd backend
mvn test                     # unit tests (no Docker needed)
mvn verify                   # unit + integration tests (starts PostGIS via Testcontainers)
mvn spring-boot:test-run     # run the API on :8080 against a throwaway PostGIS container
```

Or run against the compose database: `docker compose up db`, then
`mvn spring-boot:run`.

| Endpoint | Purpose |
| --- | --- |
| `http://localhost:8080/api/v1/system/info` | public API status |
| `http://localhost:8081/actuator/health` | health (management port, never proxied) |
| `http://localhost:8081/actuator/prometheus` | metrics |

Logs are plain text when run this way and structured JSON (ECS) in Docker. Every
line carries the request id; responses return it as `X-Request-Id`.

## Work on the frontend

```bash
cd frontend
npm ci
npm start            # http://localhost:4200, proxies /api to localhost:8080
npm test             # unit tests (Vitest)
npm run build        # production build
npm run format       # Prettier
```

All user-facing text lives in `public/i18n/en.json`.

## Environment variables

See [`.env.example`](../../.env.example): every variable is listed there with
what it does and its development default.
