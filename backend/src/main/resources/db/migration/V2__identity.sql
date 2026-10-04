CREATE TABLE teams (
    id         uuid PRIMARY KEY,
    name       text        NOT NULL,
    created_at timestamptz NOT NULL,
    CONSTRAINT teams_name_key UNIQUE (name)
);

CREATE TABLE users (
    id                 uuid PRIMARY KEY,
    email              text        NOT NULL,
    password_hash      text        NOT NULL,
    display_name       text        NOT NULL,
    team_id            uuid REFERENCES teams (id),
    enabled            boolean     NOT NULL,
    failed_login_count integer     NOT NULL DEFAULT 0,
    locked_until       timestamptz,
    last_login_at      timestamptz,
    created_at         timestamptz NOT NULL,
    CONSTRAINT users_email_key UNIQUE (email),
    CONSTRAINT users_email_lower_check CHECK (email = lower(email)),
    CONSTRAINT users_failed_login_count_check CHECK (failed_login_count >= 0)
);

CREATE INDEX users_team_id_idx ON users (team_id);
CREATE INDEX users_created_at_idx ON users (created_at DESC);

CREATE TABLE user_roles (
    user_id uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role    text NOT NULL,
    PRIMARY KEY (user_id, role),
    CONSTRAINT user_roles_role_check CHECK (role IN ('CITIZEN', 'MODERATOR', 'CITY_OPERATOR', 'ADMIN'))
);

CREATE TABLE refresh_tokens (
    id          uuid PRIMARY KEY,
    user_id     uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    family_id   uuid        NOT NULL,
    token_hash  text        NOT NULL,
    created_at  timestamptz NOT NULL,
    expires_at  timestamptz NOT NULL,
    revoked_at  timestamptz,
    replaced_by uuid,
    CONSTRAINT refresh_tokens_token_hash_key UNIQUE (token_hash)
);

CREATE INDEX refresh_tokens_user_id_idx ON refresh_tokens (user_id);
CREATE INDEX refresh_tokens_family_id_idx ON refresh_tokens (family_id);
