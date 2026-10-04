-- Extensions must exist before any table that uses them.
-- Needs a superuser; on a managed database create them once by hand.

CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE EXTENSION IF NOT EXISTS citext;
