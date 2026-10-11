# ADR 005: Map and nearby queries in PostGIS

Status: accepted
Date: 2026-10-11

## Context

The map asks the API for reports every time the user pans or zooms. Zoomed out
it would get thousands of points, more than a browser should draw. The report
form needs "is there already a report close to here?" while the user is typing.
Both have to stay fast with a lot of reports, and it is easy to write a spatial
query that quietly stops using its index.

## Decision

- `reports.location` is a `geography(Point, 4326)` column generated from
  `latitude` and `longitude`. JPA only writes the two numbers; no Hibernate
  spatial types are needed.
- A partial GIST index covers reports that are not `CLOSED`, `REJECTED` or
  `DUPLICATE`. The map and the nearby search filter with exactly that
  predicate, otherwise PostgreSQL can't use the partial index. A second GIST
  index on all reports is used when the map shows closed reports too.
- `GET /reports/nearby` uses `ST_DWithin` with a radius in metres (at most
  2 km) and orders by distance.
- `GET /reports/map?bbox=&zoom=` returns single markers from zoom 15 (at most
  500, most severe first) and grid clusters below that. A cluster is one cell
  of a quarter of a map tile, so roughly 64 px on screen. It has the count,
  the average position, the most common category and the highest severity.
- The map filters with `location && envelope` (bounding boxes only) instead of
  `ST_Intersects`. A few points just outside the screen don't matter on a map.
- The bounding box may not be much larger than a screen at the given zoom, so
  nobody can ask for the whole world at zoom 15.
- A report created or moved through the API gets its district; locations
  outside all districts are rejected with `LOCATION_OUTSIDE_CITY`. Generated
  demo reports that land just outside the outlines have no district.

## Measurements

100,000 reports spread over the city, 80,000 of them open. PostgreSQL 16.15,
PostGIS 3.4.2, 2 vCPU Xeon container, warm cache.

| Query | Time |
| --- | --- |
| Clusters for the whole city (zoom 12), first version: `ST_Intersects`, `ST_Centroid(ST_Collect(...))`, `ST_SnapToGrid` | 491 ms (270 ms with JIT off) |
| Same with `&&`, `avg()` and `floor()` cells | 71-88 ms |
| Markers in a few hundred metres (zoom 16) | 5-13 ms |
| Clusters for a small area (zoom 14) | 3-5 ms |
| Nearby, 200 m | 5-24 ms |

The first version spent most of its time on the exact intersection test and on
building geometries per cell, and its cost estimate was high enough to switch
on JIT compilation, which added another 200 ms. With `&&` the whole-city
query is a cheap sequential scan (most rows match anyway, so that is the right
plan) and small areas use the partial index.

`SpatialIndexIT` loads 100,000 reports into the test database and checks with
`EXPLAIN` that the real queries use the indexes.

## Alternatives

- `ST_ClusterKMeans` or `ST_ClusterDBSCAN`: nicer clusters, but much more work
  per request and the clusters jump around while panning.
- Clustering in the browser (Leaflet.markercluster): simple, but the browser
  would have to download every report.
- Vector tiles (`ST_AsMVT`): the right tool for very large data sets, a lot of
  extra setup for this project.
- `geometry` in a projected system (Swiss LV95): a bit faster, but ties the
  schema to one country.

## Consequences

- Grid clusters can split a group of reports that sits on a cell edge.
- The district outlines in V5 are simplified rectangles. A real deployment
  needs the official boundaries in a new migration.
- Changing the "closed" statuses means changing the partial index and the
  constant in `ReportGeoQueries` together.
