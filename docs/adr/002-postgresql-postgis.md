# 002 — Use PostgreSQL with PostGIS as the only datastore

- **Status:** accepted
- **Date:** 2026-10-03

## Context

The core queries are spatial: reports near a point, reports inside the map
viewport, clusters per zoom level, candidates for duplicate detection within a
category-specific radius, proximity to critical infrastructure. Duplicate
detection also needs text similarity between short descriptions. Dashboards need
set-based aggregation by time, category and area.

## Decision

- **PostgreSQL** (17 in development and CI) with the **PostGIS**, **pg_trgm** and
  **citext** extensions, enabled in the baseline migration `V1__baseline.sql`.
- Report locations are stored as `geography(Point, 4326)` with a GIST index
  (partial: open reports only), so `ST_DWithin` distances are in metres and the
  index is used. Details and query plans land in `docs/database/` in Phase 4.
- **Flyway owns the schema.** Hibernate runs with `ddl-auto=validate` in every
  environment. Merged migrations are never edited.
- Integration tests run against the **same PostGIS image** through Testcontainers,
  never against H2 or an in-memory substitute.

## Alternatives

- **Plain PostgreSQL with lat/lng columns and Haversine in SQL.** No spatial
  index, so every radius query scans the table. Rejected.
- **`geometry` in a projected CRS** (Swiss LV95 for the Bern seed data).
  Marginally faster planar maths, but ties the schema to one city. Rejected for
  portability; clustering casts to geometry only after index filtering.
- **Elasticsearch or a dedicated search engine** for text and geo. A second
  stateful system to run and keep consistent, for data volumes PostgreSQL
  handles comfortably. Rejected.
- **H2 for tests.** No PostGIS, different SQL dialect; tests would pass while
  production queries fail. Rejected.

## Consequences

- One stateful dependency; backups, migrations and local setup stay simple.
- Spatial logic is partly in SQL, so it is tested with real PostGIS in
  integration tests rather than only in unit tests.
- The `postgis/postgis` image is published for `linux/amd64`; Apple Silicon
  machines run it under emulation (slower, but functional).
- **Revisit when:** data volume or query load outgrows a single primary, or
  free-text search needs ranking beyond trigram similarity.
