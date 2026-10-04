CREATE TABLE audit_entries (
    id          uuid PRIMARY KEY,
    occurred_at timestamptz NOT NULL,
    actor_id    uuid,
    action      text        NOT NULL,
    target_type text        NOT NULL,
    target_id   text        NOT NULL,
    details     jsonb       NOT NULL DEFAULT '{}'::jsonb,
    request_id  text
);

CREATE INDEX audit_entries_occurred_at_idx ON audit_entries (occurred_at DESC);
CREATE INDEX audit_entries_target_idx ON audit_entries (target_type, target_id, occurred_at DESC);
CREATE INDEX audit_entries_actor_idx ON audit_entries (actor_id, occurred_at DESC);
CREATE INDEX audit_entries_action_idx ON audit_entries (action, occurred_at DESC);

CREATE FUNCTION reject_row_change() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    RAISE EXCEPTION '% is append-only', TG_TABLE_NAME;
END;
$$;

CREATE TRIGGER audit_entries_append_only
    BEFORE UPDATE OR DELETE ON audit_entries
    FOR EACH ROW EXECUTE FUNCTION reject_row_change();
