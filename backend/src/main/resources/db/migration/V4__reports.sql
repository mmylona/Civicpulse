CREATE TABLE categories (
    code       text PRIMARY KEY,
    sort_order integer NOT NULL,
    active     boolean NOT NULL DEFAULT true
);

INSERT INTO categories (code, sort_order) VALUES
    ('POTHOLE', 10),
    ('ROAD_DAMAGE', 20),
    ('STREET_LIGHT', 30),
    ('TRAFFIC_SIGNAL', 40),
    ('ILLEGAL_DUMPING', 50),
    ('OVERFLOWING_BIN', 60),
    ('GRAFFITI', 70),
    ('WATER_LEAK', 80),
    ('BLOCKED_DRAIN', 90),
    ('PARK_DAMAGE', 100),
    ('ACCESSIBILITY', 110),
    ('OTHER', 999);

-- public reference CP-10000, CP-10001, ...
CREATE SEQUENCE report_number_seq START WITH 10000;

CREATE TABLE reports (
    id                  uuid PRIMARY KEY,
    reference           text             NOT NULL,
    reporter_id         uuid             NOT NULL REFERENCES users (id),
    category_code       text             NOT NULL REFERENCES categories (code),
    title               text             NOT NULL,
    description         text             NOT NULL,
    stated_severity     text             NOT NULL,
    validated_severity  text,
    latitude            double precision NOT NULL,
    longitude           double precision NOT NULL,
    -- derived, so JPA only deals with plain numbers
    location            geography(Point, 4326) GENERATED ALWAYS AS (
                            ST_SetSRID(ST_MakePoint(longitude, latitude), 4326)::geography) STORED,
    address             text,
    status              text             NOT NULL,
    resume_status       text,
    team_id             uuid REFERENCES teams (id),
    assignee_id         uuid REFERENCES users (id),
    duplicate_of_id     uuid REFERENCES reports (id),
    submission_key      text,
    still_present_count integer          NOT NULL DEFAULT 0,
    fixed_count         integer          NOT NULL DEFAULT 0,
    created_at          timestamptz      NOT NULL,
    updated_at          timestamptz      NOT NULL,
    resolved_at         timestamptz,
    closed_at           timestamptz,
    version             bigint           NOT NULL DEFAULT 0,
    CONSTRAINT reports_reference_key UNIQUE (reference),
    CONSTRAINT reports_submission_key UNIQUE (reporter_id, submission_key),
    CONSTRAINT reports_status_check CHECK (status IN (
        'REPORTED', 'TRIAGED', 'ASSIGNED', 'IN_PROGRESS', 'WAITING_FOR_INFORMATION',
        'RESOLVED', 'CLOSED', 'REJECTED', 'DUPLICATE')),
    CONSTRAINT reports_resume_status_check CHECK (resume_status IN ('REPORTED', 'TRIAGED', 'ASSIGNED', 'IN_PROGRESS')),
    CONSTRAINT reports_waiting_check CHECK ((status = 'WAITING_FOR_INFORMATION') = (resume_status IS NOT NULL)),
    CONSTRAINT reports_stated_severity_check CHECK (stated_severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    CONSTRAINT reports_validated_severity_check CHECK (validated_severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    CONSTRAINT reports_team_check CHECK (status NOT IN ('ASSIGNED', 'IN_PROGRESS') OR team_id IS NOT NULL),
    CONSTRAINT reports_duplicate_check CHECK ((status = 'DUPLICATE') = (duplicate_of_id IS NOT NULL)),
    CONSTRAINT reports_duplicate_self_check CHECK (duplicate_of_id <> id),
    CONSTRAINT reports_title_check CHECK (char_length(title) BETWEEN 1 AND 120),
    CONSTRAINT reports_description_check CHECK (char_length(description) BETWEEN 1 AND 4000),
    CONSTRAINT reports_latitude_check CHECK (latitude BETWEEN -90 AND 90),
    CONSTRAINT reports_longitude_check CHECK (longitude BETWEEN -180 AND 180),
    CONSTRAINT reports_counts_check CHECK (still_present_count >= 0 AND fixed_count >= 0)
);

CREATE INDEX reports_created_at_idx ON reports (created_at DESC);
CREATE INDEX reports_status_idx ON reports (status, created_at DESC);
CREATE INDEX reports_team_status_idx ON reports (team_id, status);
CREATE INDEX reports_reporter_idx ON reports (reporter_id, created_at DESC);
CREATE INDEX reports_category_idx ON reports (category_code, created_at DESC);
CREATE INDEX reports_assignee_idx ON reports (assignee_id) WHERE assignee_id IS NOT NULL;
CREATE INDEX reports_duplicate_of_idx ON reports (duplicate_of_id) WHERE duplicate_of_id IS NOT NULL;
CREATE INDEX reports_resolved_idx ON reports (resolved_at) WHERE status = 'RESOLVED';

CREATE TABLE report_events (
    seq         bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id          uuid        NOT NULL,
    report_id   uuid        NOT NULL REFERENCES reports (id),
    type        text        NOT NULL,
    visibility  text        NOT NULL,
    actor_id    uuid REFERENCES users (id),
    actor_kind  text        NOT NULL,
    payload     jsonb       NOT NULL DEFAULT '{}'::jsonb,
    occurred_at timestamptz NOT NULL,
    CONSTRAINT report_events_id_key UNIQUE (id),
    CONSTRAINT report_events_type_check CHECK (type IN ('SUBMITTED', 'EDITED', 'STATUS_CHANGED', 'NOTE')),
    CONSTRAINT report_events_visibility_check CHECK (visibility IN ('PUBLIC', 'INTERNAL')),
    CONSTRAINT report_events_actor_kind_check CHECK (actor_kind IN ('REPORTER', 'CITIZEN', 'STAFF', 'SYSTEM'))
);

CREATE INDEX report_events_report_idx ON report_events (report_id, seq);
CREATE INDEX report_events_actor_idx ON report_events (actor_id) WHERE actor_id IS NOT NULL;

CREATE TRIGGER report_events_append_only
    BEFORE UPDATE OR DELETE ON report_events
    FOR EACH ROW EXECUTE FUNCTION reject_row_change();

CREATE TABLE report_comments (
    id            uuid PRIMARY KEY,
    report_id     uuid        NOT NULL REFERENCES reports (id),
    author_id     uuid        NOT NULL REFERENCES users (id),
    author_kind   text        NOT NULL,
    visibility    text        NOT NULL,
    body          text        NOT NULL,
    created_at    timestamptz NOT NULL,
    hidden_at     timestamptz,
    hidden_by     uuid REFERENCES users (id),
    hidden_reason text,
    CONSTRAINT report_comments_author_kind_check CHECK (author_kind IN ('REPORTER', 'CITIZEN', 'STAFF')),
    CONSTRAINT report_comments_visibility_check CHECK (visibility IN ('PUBLIC', 'INTERNAL')),
    CONSTRAINT report_comments_body_check CHECK (char_length(body) BETWEEN 1 AND 2000),
    CONSTRAINT report_comments_hidden_check CHECK ((hidden_at IS NULL) = (hidden_by IS NULL))
);

CREATE INDEX report_comments_report_idx ON report_comments (report_id, created_at);
CREATE INDEX report_comments_author_idx ON report_comments (author_id);
CREATE INDEX report_comments_hidden_by_idx ON report_comments (hidden_by) WHERE hidden_by IS NOT NULL;

CREATE TABLE confirmations (
    report_id  uuid        NOT NULL REFERENCES reports (id),
    user_id    uuid        NOT NULL REFERENCES users (id),
    kind       text        NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    PRIMARY KEY (report_id, user_id),
    CONSTRAINT confirmations_kind_check CHECK (kind IN ('STILL_PRESENT', 'FIXED'))
);

CREATE INDEX confirmations_user_idx ON confirmations (user_id);
