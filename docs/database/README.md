# Database

PostgreSQL 17 with PostGIS, pg_trgm and citext ([ADR 002](../adr/002-postgresql-postgis.md)).

## Migrations

- Location: `backend/src/main/resources/db/migration`
- Naming: `V<n>__<description>.sql`
- Merged migrations are never edited; add a new one instead.
- Hibernate only validates the schema (`ddl-auto=validate`).
- `FlywayMigrationIT` migrates an empty database on every build.

| Version | Change |
| --- | --- |
| V1 | enable `postgis`, `pg_trgm`, `citext` |
| V2 | `teams`, `users`, `user_roles`, `refresh_tokens` |
| V3 | `audit_entries` and the `reject_row_change()` trigger function |
| V4 | `categories`, `reports`, `report_events`, `report_comments`, `confirmations` |

## Tables

```mermaid
erDiagram
    teams ||--o{ users : "has members"
    users ||--|{ user_roles : has
    users ||--o{ refresh_tokens : "signs in with"
    users ||--o{ audit_entries : "acts in (actor_id)"

    users {
        uuid id PK
        text email UK "stored lower-case"
        text password_hash
        text display_name
        uuid team_id FK
        boolean enabled
        int failed_login_count
        timestamptz locked_until
    }
    refresh_tokens {
        uuid id PK
        uuid user_id FK
        uuid family_id "one per sign-in"
        text token_hash UK "SHA-256"
        timestamptz expires_at
        timestamptz revoked_at
        uuid replaced_by
    }
    audit_entries {
        uuid id PK
        timestamptz occurred_at
        uuid actor_id
        text action
        text target_type
        text target_id
        jsonb details
        text request_id
    }
```

- Emails are saved in lower case (`CHECK (email = lower(email))`), so the unique
  key works case-insensitively.
- `user_roles.role` must be one of the four roles.
- `audit_entries` is append-only (trigger). `actor_id` has no foreign key so
  audit rows never block anything else.
- Refresh tokens with the same `family_id` come from the same login.

### Reports

```mermaid
erDiagram
    categories ||--o{ reports : "category_code"
    users ||--o{ reports : "reports (reporter_id)"
    teams ||--o{ reports : "works on (team_id)"
    reports ||--o{ reports : "duplicate_of_id"
    reports ||--o{ report_events : "timeline"
    reports ||--o{ report_comments : "comments"
    reports ||--o{ confirmations : "confirmed by"
    users ||--o{ confirmations : "confirms"

    reports {
        uuid id PK
        text reference UK "CP-10000"
        uuid reporter_id FK
        text category_code FK
        text status
        text resume_status "only while waiting"
        text stated_severity
        text validated_severity "set at triage"
        float8 latitude
        float8 longitude
        geography location "generated"
        uuid team_id FK
        uuid assignee_id FK
        uuid duplicate_of_id FK
        int still_present_count
        int fixed_count
        bigint version "ETag"
    }
    report_events {
        bigint seq PK
        uuid id UK
        uuid report_id FK
        text type
        text visibility
        uuid actor_id FK
        text actor_kind
        jsonb payload
    }
    report_comments {
        uuid id PK
        uuid report_id FK
        uuid author_id FK
        text visibility
        text body
        timestamptz hidden_at
    }
    confirmations {
        uuid report_id PK
        uuid user_id PK
        text kind
        timestamptz updated_at
    }
```

- The lifecycle rules are in code ([ADR 004](../adr/004-report-lifecycle.md));
  the database adds checks that hold in every status: `resume_status` is set
  only while waiting, `DUPLICATE` always has `duplicate_of_id`, `ASSIGNED` and
  `IN_PROGRESS` always have a team.
- `location` is generated from `latitude` and `longitude`, so JPA only maps
  plain numbers. The spatial index comes with the geo queries.
- `report_events` is append-only like `audit_entries`. `seq` keeps the order of
  entries written in the same millisecond.
- `still_present_count` and `fixed_count` are only written by an SQL recount
  after a confirmation changes; the JPA mapping is read-only for them.
- `version` is incremented by Hibernate on every update and used as the ETag.
- References come from the sequence `report_number_seq`, starting at 10000.
- Categories are seeded by the migration. Their labels are translated in the
  frontend.

## Conventions

- Primary keys: UUID v7 generated in the application (`Ids.newId()`), which
  are time-ordered and index-friendly. Lookup tables with a natural code use
  the code.
- Timestamps: `timestamptz`, UTC.
- Enumerations: `text` with a `CHECK` constraint.
- Locations: `geography(Point, 4326)`, generated from latitude/longitude where
  the application writes them.
- Every foreign key column is indexed.
