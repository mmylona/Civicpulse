# Local setup

## Requirements

- Docker with Compose v2
- Java 21 and Maven 3.9+ (backend)
- Node.js 22.22.3+ or 24.15+ (frontend, Angular 22)

## Run everything

```bash
docker compose up --build
```

Open <http://localhost:8080>. No `.env` file is needed; copy `.env.example` to
`.env` to change ports or credentials.

The database is on `localhost:5433` for SQL clients (5433 so it doesn't clash
with a local PostgreSQL). On Apple Silicon the PostGIS image runs under
emulation, so the first start is slow.

`docker compose down -v` stops everything and deletes the database volume.

## Backend

```bash
cd backend
mvn test                     # unit tests
mvn verify                   # unit + integration tests (needs Docker)
mvn spring-boot:test-run     # run on :8080 with a temporary PostGIS container
```

Or start only the database with `docker compose up db` and run:

```bash
CIVICPULSE_JWT_SECRET=local-dev-secret-at-least-32-bytes-long \
CIVICPULSE_DB_URL=jdbc:postgresql://localhost:5433/civicpulse \
SPRING_PROFILES_ACTIVE=demo \
mvn spring-boot:run
```

The backend refuses to start without a JWT secret of at least 32 bytes. The
`demo` profile creates the demo accounts listed in the README.

| URL | |
| --- | --- |
| `http://localhost:8080/api/v1/system/info` | API status |
| `http://localhost:8081/actuator/health` | health |
| `http://localhost:8081/actuator/prometheus` | metrics |

## Frontend

```bash
cd frontend
npm ci
npm start          # http://localhost:4200, proxies /api to localhost:8080
npm test
npm run build
npm run format
```

Translations are in `public/i18n/en.json`.
