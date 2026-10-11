-- The six districts (Stadtteile) of Bern as simplified outlines. They are made of
-- rectangles that share their edges, good enough to tell the parts of the city
-- apart, but not the official boundaries.
CREATE TABLE districts (
    code     text PRIMARY KEY,
    name     text                         NOT NULL,
    boundary geometry(MultiPolygon, 4326) NOT NULL
);

CREATE INDEX districts_boundary_idx ON districts USING GIST (boundary);

INSERT INTO districts (code, name, boundary) VALUES
    ('innere-stadt', 'Innere Stadt', ST_Multi(ST_GeomFromText(
        'POLYGON((7.437 46.944, 7.462 46.944, 7.462 46.953, 7.437 46.953, 7.437 46.944))', 4326))),
    ('laenggasse-felsenau', 'Länggasse-Felsenau', ST_Multi(ST_GeomFromText(
        'POLYGON((7.400 46.950, 7.437 46.950, 7.437 46.953, 7.445 46.953, 7.445 46.985, 7.400 46.985, 7.400 46.950))', 4326))),
    ('mattenhof-weissenbuehl', 'Mattenhof-Weissenbühl', ST_Multi(ST_GeomFromText(
        'POLYGON((7.400 46.925, 7.445 46.925, 7.445 46.944, 7.437 46.944, 7.437 46.950, 7.400 46.950, 7.400 46.925))', 4326))),
    ('kirchenfeld-schosshalde', 'Kirchenfeld-Schosshalde', ST_Multi(ST_GeomFromText(
        'POLYGON((7.445 46.925, 7.495 46.925, 7.495 46.953, 7.462 46.953, 7.462 46.944, 7.445 46.944, 7.445 46.925))', 4326))),
    ('breitenrain-lorraine', 'Breitenrain-Lorraine', ST_Multi(ST_GeomFromText(
        'POLYGON((7.445 46.953, 7.480 46.953, 7.480 46.975, 7.445 46.975, 7.445 46.953))', 4326))),
    ('buempliz-oberbottigen', 'Bümpliz-Oberbottigen', ST_Multi(ST_GeomFromText(
        'POLYGON((7.295 46.925, 7.400 46.925, 7.400 46.965, 7.295 46.965, 7.295 46.925))', 4326)));

-- Places near which a problem is more urgent. Positions are approximate.
CREATE TABLE critical_sites (
    id       uuid PRIMARY KEY,
    kind     text                   NOT NULL,
    name     text                   NOT NULL,
    location geography(Point, 4326) NOT NULL,
    weight   integer                NOT NULL,
    CONSTRAINT critical_sites_kind_check CHECK (kind IN ('HOSPITAL', 'SCHOOL', 'CARE_HOME', 'TRANSIT_HUB')),
    CONSTRAINT critical_sites_weight_check CHECK (weight BETWEEN 0 AND 15)
);

CREATE INDEX critical_sites_location_idx ON critical_sites USING GIST (location);

INSERT INTO critical_sites (id, kind, name, location, weight)
SELECT gen_random_uuid(), kind, name, ST_SetSRID(ST_MakePoint(lng, lat), 4326)::geography, weight
FROM (VALUES
    ('HOSPITAL', 'Inselspital', 46.9473, 7.4240, 15),
    ('HOSPITAL', 'Lindenhofspital', 46.9573, 7.4310, 15),
    ('HOSPITAL', 'Salem-Spital', 46.9575, 7.4515, 15),
    ('HOSPITAL', 'Tiefenauspital', 46.9725, 7.4375, 15),
    ('TRANSIT_HUB', 'Bern station', 46.9490, 7.4391, 10),
    ('TRANSIT_HUB', 'Wankdorf station', 46.9680, 7.4660, 10),
    ('TRANSIT_HUB', 'Europaplatz station', 46.9430, 7.4040, 10),
    ('TRANSIT_HUB', 'Bümpliz Süd station', 46.9375, 7.3900, 10),
    ('SCHOOL', 'Gymnasium Kirchenfeld', 46.9418, 7.4510, 10),
    ('SCHOOL', 'Gymnasium Neufeld', 46.9620, 7.4275, 10),
    ('SCHOOL', 'University of Bern, main building', 46.9505, 7.4380, 8)
) AS sites (kind, name, lat, lng, weight);

ALTER TABLE reports ADD COLUMN district_code text REFERENCES districts (code);

UPDATE reports r
SET district_code = d.code
FROM districts d
WHERE ST_Covers(d.boundary, r.location::geometry);

CREATE INDEX reports_district_idx ON reports (district_code, created_at DESC);

-- Map and nearby queries only look at reports that still matter; the predicate
-- must stay exactly the same as in ReportGeoQueries or the index is not used.
CREATE INDEX reports_open_location_idx ON reports USING GIST (location)
    WHERE status NOT IN ('CLOSED', 'REJECTED', 'DUPLICATE');

-- for the map with closed reports switched on
CREATE INDEX reports_location_idx ON reports USING GIST (location);
