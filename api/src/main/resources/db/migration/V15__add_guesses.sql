-- V15__add_guesses.sql
-- GM-015: captain guess submissions with neutral receipts (verdicts stay hidden until finale).

CREATE TABLE murise.guesses (
                                id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
                                event_id         uuid NOT NULL REFERENCES murise.events (id),
                                guessing_team_id uuid NOT NULL,
                                target_scene_id  uuid NOT NULL,
                                stage            integer NOT NULL CHECK (stage BETWEEN 0 AND 3),
                                raw_guess        text NOT NULL
                                    CHECK (btrim(raw_guess) <> '' AND char_length(raw_guess) <= 160),
                                normalized_guess text NOT NULL
                                    CHECK (btrim(normalized_guess) <> ''),
                                submitted_by     uuid NOT NULL REFERENCES murise.profiles (id),
                                submitted_at     timestamptz NOT NULL DEFAULT now(),
                                request_id       uuid NOT NULL,
                                UNIQUE (event_id, guessing_team_id, target_scene_id, stage),
                                UNIQUE (event_id, request_id),
                                FOREIGN KEY (event_id, guessing_team_id)
                                    REFERENCES murise.teams (event_id, id),
                                FOREIGN KEY (event_id, target_scene_id)
                                    REFERENCES murise.scene_entries (event_id, id)
);

CREATE INDEX ix_guesses_event_team
    ON murise.guesses (event_id, guessing_team_id, stage);

CREATE TRIGGER protect_guesses
    BEFORE UPDATE OR DELETE OR TRUNCATE ON murise.guesses
    FOR EACH STATEMENT EXECUTE FUNCTION murise.reject_history_mutation();

ALTER TABLE murise.guesses ENABLE ROW LEVEL SECURITY;

REVOKE ALL ON TABLE murise.guesses
    FROM PUBLIC, anon, authenticated, murise_app;

GRANT SELECT, INSERT ON TABLE murise.guesses TO murise_app;

CREATE POLICY murise_app_runtime_access ON murise.guesses
    FOR ALL TO murise_app USING (true) WITH CHECK (true);