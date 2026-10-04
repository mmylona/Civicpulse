# ADR 002: PostgreSQL and PostGIS

Status: accepted
Date: 2026-10-03

## Context

Most important queries are spatial: reports near a point, reports in the visible
map area, clusters per zoom level, duplicate candidates within a radius, distance
to hospitals and schools. Duplicate detection also compares short descriptions.
Dashboards aggregate by time, category and area.

## Decision

- PostgreSQL 17 with the PostGIS, pg_trgm and citext extensions, created in
  `V1__baseline.sql`.
- Report locations are `geography(Point, 4326)` with a GIST index, so
  `ST_DWithin` works in metres and uses the index.
- Flyway manages the schema. Hibernate runs with `ddl-auto=validate`.
- Integration tests use the same PostGIS image through Testcontainers, not H2.

## Alternatives

- Latitude/longitude columns with Haversine in SQL: no spatial index, so every
  radius query scans the table.
- `geometry` in a projected CRS (Swiss LV95 for Bern): slightly faster, but ties
  the schema to one city.
- Elasticsearch for text and geo search: a second datastore to run and keep in
  sync, which this data volume doesn't need.
- H2 in tests: no PostGIS and a different SQL dialect, so tests could pass while
  real queries fail.

## Consequences

- One stateful service to run, back up and migrate.
- Part of the spatial logic is in SQL and is covered by integration tests.
- The `postgis/postgis` image is amd64 only and runs under emulation on Apple
  Silicon.
- Revisit if load outgrows a single database or search needs proper ranking.
