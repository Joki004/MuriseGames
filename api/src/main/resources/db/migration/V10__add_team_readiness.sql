-- V10__add_team_readiness.sql
-- Tracks per-team readiness for the current round/activity (GM-025 / GM-026).

CREATE TABLE murise.event_team_readiness (
                                             event_id   uuid NOT NULL REFERENCES murise.events (id),
                                             team_id    uuid NOT NULL,
                                             ready      boolean NOT NULL DEFAULT false,
                                             updated_by uuid NOT NULL REFERENCES murise.profiles (id),
                                             updated_at timestamptz NOT NULL DEFAULT now(),
                                             PRIMARY KEY (event_id, team_id),
                                             FOREIGN KEY (event_id, team_id)
                                                 REFERENCES murise.teams (event_id, id)
);

CREATE TRIGGER set_updated_at
    BEFORE UPDATE ON murise.event_team_readiness
    FOR EACH ROW
EXECUTE FUNCTION murise.touch_updated_at();

ALTER TABLE murise.event_team_readiness ENABLE ROW LEVEL SECURITY;

REVOKE ALL ON TABLE murise.event_team_readiness
    FROM PUBLIC, anon, authenticated, murise_app;

GRANT SELECT, INSERT, UPDATE, DELETE
    ON TABLE murise.event_team_readiness
    TO murise_app;

CREATE POLICY murise_app_runtime_access
    ON murise.event_team_readiness
    FOR ALL
    TO murise_app
    USING (true)
    WITH CHECK (true);