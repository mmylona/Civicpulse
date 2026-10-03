-- V1: baseline.
-- Enables the extensions the schema depends on (ADR-002). Tables arrive with the modules
-- that own them, starting with identity in V2.
--
-- Creating extensions requires elevated privileges. The development and test databases
-- run as the container superuser; in a managed environment a DBA creates them once and
-- these statements become no-ops.

CREATE EXTENSION IF NOT EXISTS postgis;   -- geography types, spatial indexes, ST_DWithin
CREATE EXTENSION IF NOT EXISTS pg_trgm;   -- trigram similarity for duplicate detection
CREATE EXTENSION IF NOT EXISTS citext;    -- case-insensitive e-mail addresses
