-- V14__add_guessing_state_and_clue_releases.sql
-- GM-014: atomic clue stage releases and shared guessing_state lock row.

CREATE TABLE murise.guessing_state (
                                       event_id      uuid PRIMARY KEY REFERENCES murise.events (id),
                                       current_stage integer NOT NULL DEFAULT 0
                                           CHECK (current_stage BETWEEN 0 AND 3),
                                       status        text NOT NULL DEFAULT 'open'
                                           CHECK (status IN ('open', 'closed')),
                                       version       bigint NOT NULL DEFAULT 0
                                           CHECK (version >= 0),
                                       updated_at    timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE murise.clue_releases (
                                      id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
                                      event_id    uuid NOT NULL REFERENCES murise.events (id),
                                      stage       integer NOT NULL CHECK (stage BETWEEN 1 AND 3),
                                      request_id  uuid NOT NULL,
                                      released_by uuid NOT NULL REFERENCES murise.profiles (id),
                                      released_at timestamptz NOT NULL DEFAULT now(),
                                      UNIQUE (event_id, stage),
                                      UNIQUE (event_id, request_id)
);

CREATE TRIGGER set_updated_at
    BEFORE UPDATE ON murise.guessing_state
    FOR EACH ROW EXECUTE FUNCTION murise.touch_updated_at();

CREATE TRIGGER protect_clue_releases
    BEFORE UPDATE OR DELETE OR TRUNCATE ON murise.clue_releases
    FOR EACH STATEMENT EXECUTE FUNCTION murise.reject_history_mutation();

ALTER TABLE murise.guessing_state ENABLE ROW LEVEL SECURITY;
ALTER TABLE murise.clue_releases  ENABLE ROW LEVEL SECURITY;

REVOKE ALL ON TABLE murise.guessing_state, murise.clue_releases
    FROM PUBLIC, anon, authenticated, murise_app;

GRANT SELECT, INSERT, UPDATE ON TABLE murise.guessing_state TO murise_app;
GRANT SELECT, INSERT         ON TABLE murise.clue_releases  TO murise_app;

CREATE POLICY murise_app_runtime_access ON murise.guessing_state
    FOR ALL TO murise_app USING (true) WITH CHECK (true);

CREATE POLICY murise_app_runtime_access ON murise.clue_releases
    FOR ALL TO murise_app USING (true) WITH CHECK (true);