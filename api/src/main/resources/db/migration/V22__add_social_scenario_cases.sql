-- V22__add_social_scenario_cases.sql

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

CREATE TABLE murise.social_cases (
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

CREATE INDEX ix_social_cases_event ON murise.social_cases (event_id, sort_order);

CREATE TABLE murise.social_case_versions (
    id                   uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id             uuid NOT NULL,
    case_id              uuid NOT NULL,
    version_number       integer NOT NULL CHECK (version_number > 0),
    public_setup         text NOT NULL CHECK (btrim(public_setup) <> ''),
    defender_goals       jsonb NOT NULL DEFAULT '[]'::jsonb,
    defender_constraints jsonb NOT NULL DEFAULT '[]'::jsonb,
    attacker_red_flags   jsonb NOT NULL DEFAULT '[]'::jsonb,
    attacker_bait        jsonb NOT NULL DEFAULT '[]'::jsonb,
    judge_rubric         jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_by           uuid NOT NULL REFERENCES murise.profiles (id),
    created_at           timestamptz NOT NULL DEFAULT now(),
    UNIQUE (case_id, version_number),
    UNIQUE (event_id, id),
    FOREIGN KEY (event_id, case_id)
        REFERENCES murise.social_cases (event_id, id)
);

CREATE INDEX ix_social_case_versions_case
    ON murise.social_case_versions (case_id, version_number DESC);

-- Tracks everyone who opened or edited a case so their team will not receive it.
CREATE TABLE murise.social_case_viewers (
    case_id       uuid NOT NULL,
    event_id      uuid NOT NULL,
    user_id       uuid NOT NULL REFERENCES murise.profiles (id),
    first_seen_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (case_id, user_id),
    FOREIGN KEY (event_id, case_id)
        REFERENCES murise.social_cases (event_id, id)
);

CREATE INDEX ix_social_case_viewers_event ON murise.social_case_viewers (event_id, user_id);

CREATE TABLE murise.social_case_access_requests (
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
        REFERENCES murise.social_cases (event_id, id),
    CHECK ((status = 'pending') = (decided_at IS NULL)),
    CHECK ((status = 'pending') = (decided_by IS NULL))
);

CREATE UNIQUE INDEX uq_social_access_pending
    ON murise.social_case_access_requests (case_id, requester_id)
    WHERE status = 'pending';

CREATE INDEX ix_social_access_event
    ON murise.social_case_access_requests (event_id, status, created_at);

-- Active role assignments for GM-021 role-card projection (DEFENDER, ATTACKER, JUDGE, AUDIENCE).
CREATE TABLE murise.social_role_assignments (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id        uuid NOT NULL REFERENCES murise.events (id),
    fixture_id      uuid,
    round_number    integer NOT NULL DEFAULT 1 CHECK (round_number >= 1),
    case_id         uuid NOT NULL,
    case_version_id uuid NOT NULL,
    user_id         uuid NOT NULL REFERENCES murise.profiles (id),
    team_id         uuid,
    role            text NOT NULL
        CHECK (role IN ('DEFENDER', 'ATTACKER', 'JUDGE', 'AUDIENCE')),
    active          boolean NOT NULL DEFAULT true,
    assigned_by     uuid NOT NULL REFERENCES murise.profiles (id),
    assigned_at     timestamptz NOT NULL DEFAULT now(),
    FOREIGN KEY (event_id, case_id)
        REFERENCES murise.social_cases (event_id, id),
    FOREIGN KEY (event_id, case_version_id)
        REFERENCES murise.social_case_versions (event_id, id),
    FOREIGN KEY (event_id, fixture_id)
        REFERENCES murise.fixtures (event_id, id),
    FOREIGN KEY (event_id, team_id)
        REFERENCES murise.teams (event_id, id)
);

CREATE UNIQUE INDEX uq_social_active_role_per_user
    ON murise.social_role_assignments (event_id, round_number, user_id)
    WHERE active = true;

CREATE INDEX ix_social_role_assignments_event
    ON murise.social_role_assignments (event_id, round_number, active);

CREATE TRIGGER set_updated_at
    BEFORE UPDATE ON murise.social_cases
    FOR EACH ROW EXECUTE FUNCTION murise.touch_updated_at();

-- Saved versions are immutable history and cannot be updated, deleted, or truncated.
CREATE TRIGGER protect_social_case_versions
    BEFORE UPDATE OR DELETE OR TRUNCATE ON murise.social_case_versions
    FOR EACH STATEMENT
EXECUTE FUNCTION murise.reject_history_mutation();

ALTER TABLE murise.social_cases ENABLE ROW LEVEL SECURITY;
ALTER TABLE murise.social_case_versions ENABLE ROW LEVEL SECURITY;
ALTER TABLE murise.social_case_viewers ENABLE ROW LEVEL SECURITY;
ALTER TABLE murise.social_case_access_requests ENABLE ROW LEVEL SECURITY;
ALTER TABLE murise.social_role_assignments ENABLE ROW LEVEL SECURITY;

REVOKE ALL ON TABLE
    murise.social_cases,
    murise.social_case_versions,
    murise.social_case_viewers,
    murise.social_case_access_requests,
    murise.social_role_assignments
    FROM PUBLIC, anon, authenticated, murise_app;

GRANT SELECT, INSERT, UPDATE ON TABLE murise.social_cases TO murise_app;
GRANT SELECT, INSERT ON TABLE murise.social_case_versions TO murise_app;
GRANT SELECT, INSERT ON TABLE murise.social_case_viewers TO murise_app;
GRANT SELECT, INSERT, UPDATE ON TABLE murise.social_case_access_requests TO murise_app;
GRANT SELECT, INSERT, UPDATE ON TABLE murise.social_role_assignments TO murise_app;

CREATE POLICY murise_app_runtime_access ON murise.social_cases
    FOR ALL TO murise_app USING (true) WITH CHECK (true);
CREATE POLICY murise_app_runtime_access ON murise.social_case_versions
    FOR ALL TO murise_app USING (true) WITH CHECK (true);
CREATE POLICY murise_app_runtime_access ON murise.social_case_viewers
    FOR ALL TO murise_app USING (true) WITH CHECK (true);
CREATE POLICY murise_app_runtime_access ON murise.social_case_access_requests
    FOR ALL TO murise_app USING (true) WITH CHECK (true);
CREATE POLICY murise_app_runtime_access ON murise.social_role_assignments
    FOR ALL TO murise_app USING (true) WITH CHECK (true);