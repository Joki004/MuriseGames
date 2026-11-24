-- V19__add_alibi_cases.sql
-- GM-019: organizers author Unified Alibi cases (brief, fixed facts, ten questions,
-- valid equivalents and judge notes). Every saved change creates a new immutable
-- version, so a round can pin the exact version it was played with.
--
-- New event role CASE_WRITER: a helper who may only create Alibi cases and open the
-- cases they wrote themselves. It gives no other organizer access and can be revoked.
--
-- Only the event creator opens any case directly. Everyone else (co-organizers and
-- case writers) opens their own cases; for any other case they send a request with a
-- reason, and the event creator approves or refuses it.

DO $$
DECLARE
    constraint_name text;
BEGIN
    SELECT c.conname INTO constraint_name
    FROM pg_constraint c
    WHERE c.conrelid = 'murise.event_role_grants'::regclass
      AND c.contype = 'c'
      AND pg_get_constraintdef(c.oid) LIKE '%ORGANIZER%';

    IF constraint_name IS NOT NULL THEN
        EXECUTE format('ALTER TABLE murise.event_role_grants DROP CONSTRAINT %I', constraint_name);
    END IF;
END
$$;

ALTER TABLE murise.event_role_grants
    ADD CONSTRAINT event_role_grants_role_check
        CHECK (role IN ('ORGANIZER', 'REFEREE', 'PLAYER', 'VIEWER', 'CASE_WRITER'));

CREATE TABLE murise.alibi_cases (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id        uuid NOT NULL REFERENCES murise.events (id),
    label           text NOT NULL
        CHECK (btrim(label) <> '' AND char_length(label) <= 80),
    sort_order      integer NOT NULL DEFAULT 0 CHECK (sort_order >= 0),
    current_version integer NOT NULL DEFAULT 0 CHECK (current_version >= 0),
    created_by      uuid NOT NULL REFERENCES murise.profiles (id),
    archived_at     timestamptz,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    UNIQUE (event_id, id)
);

CREATE INDEX ix_alibi_cases_event ON murise.alibi_cases (event_id, sort_order);

CREATE TABLE murise.alibi_case_versions (
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id       uuid NOT NULL,
    case_id        uuid NOT NULL,
    version_number integer NOT NULL CHECK (version_number > 0),
    content        jsonb NOT NULL,
    created_by     uuid NOT NULL REFERENCES murise.profiles (id),
    created_at     timestamptz NOT NULL DEFAULT now(),
    UNIQUE (case_id, version_number),
    FOREIGN KEY (event_id, case_id)
        REFERENCES murise.alibi_cases (event_id, id)
);

CREATE INDEX ix_alibi_case_versions_case
    ON murise.alibi_case_versions (case_id, version_number DESC);

-- Everyone who has opened or edited a case knows its content. GM-046 uses this so a
-- team is never given a case that one of its own members has seen.
CREATE TABLE murise.alibi_case_viewers (
    case_id       uuid NOT NULL,
    event_id      uuid NOT NULL,
    user_id       uuid NOT NULL REFERENCES murise.profiles (id),
    first_seen_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (case_id, user_id),
    FOREIGN KEY (event_id, case_id)
        REFERENCES murise.alibi_cases (event_id, id)
);

CREATE INDEX ix_alibi_case_viewers_event ON murise.alibi_case_viewers (event_id, user_id);

CREATE TABLE murise.alibi_case_access_requests (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id      uuid NOT NULL,
    case_id       uuid NOT NULL,
    requester_id  uuid NOT NULL REFERENCES murise.profiles (id),
    reason        text NOT NULL
        CHECK (btrim(reason) <> '' AND char_length(reason) <= 500),
    status        text NOT NULL DEFAULT 'pending'
        CHECK (status IN ('pending', 'approved', 'refused')),
    decided_by    uuid REFERENCES murise.profiles (id),
    decided_at    timestamptz,
    decision_note text CHECK (decision_note IS NULL OR char_length(decision_note) <= 500),
    created_at    timestamptz NOT NULL DEFAULT now(),
    FOREIGN KEY (event_id, case_id)
        REFERENCES murise.alibi_cases (event_id, id),
    CHECK ((status = 'pending') = (decided_at IS NULL)),
    CHECK ((status = 'pending') = (decided_by IS NULL))
);

-- One open request per person and case.
CREATE UNIQUE INDEX uq_alibi_access_pending
    ON murise.alibi_case_access_requests (case_id, requester_id)
    WHERE status = 'pending';

CREATE INDEX ix_alibi_access_event
    ON murise.alibi_case_access_requests (event_id, status, created_at);

CREATE TRIGGER set_updated_at
    BEFORE UPDATE ON murise.alibi_cases
    FOR EACH ROW EXECUTE FUNCTION murise.touch_updated_at();

-- A version that may have been played is history: it can never change.
CREATE TRIGGER protect_alibi_case_versions
    BEFORE UPDATE OR DELETE OR TRUNCATE ON murise.alibi_case_versions
    FOR EACH STATEMENT
EXECUTE FUNCTION murise.reject_history_mutation();

ALTER TABLE murise.alibi_cases ENABLE ROW LEVEL SECURITY;
ALTER TABLE murise.alibi_case_versions ENABLE ROW LEVEL SECURITY;
ALTER TABLE murise.alibi_case_viewers ENABLE ROW LEVEL SECURITY;
ALTER TABLE murise.alibi_case_access_requests ENABLE ROW LEVEL SECURITY;

REVOKE ALL ON TABLE
    murise.alibi_cases, murise.alibi_case_versions, murise.alibi_case_viewers,
    murise.alibi_case_access_requests
    FROM PUBLIC, anon, authenticated, murise_app;

GRANT SELECT, INSERT, UPDATE ON TABLE murise.alibi_cases TO murise_app;
GRANT SELECT, INSERT ON TABLE murise.alibi_case_versions TO murise_app;
GRANT SELECT, INSERT ON TABLE murise.alibi_case_viewers TO murise_app;
GRANT SELECT, INSERT, UPDATE ON TABLE murise.alibi_case_access_requests TO murise_app;

CREATE POLICY murise_app_runtime_access ON murise.alibi_cases
    FOR ALL TO murise_app USING (true) WITH CHECK (true);
CREATE POLICY murise_app_runtime_access ON murise.alibi_case_versions
    FOR ALL TO murise_app USING (true) WITH CHECK (true);
CREATE POLICY murise_app_runtime_access ON murise.alibi_case_viewers
    FOR ALL TO murise_app USING (true) WITH CHECK (true);
CREATE POLICY murise_app_runtime_access ON murise.alibi_case_access_requests
    FOR ALL TO murise_app USING (true) WITH CHECK (true);
