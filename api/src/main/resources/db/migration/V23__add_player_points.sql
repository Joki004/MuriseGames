-- V23__add_player_points.sql
-- GM-067: personal points ("Star players"), separate from team scores.
-- A referee credits players per match segment; corrections are reversal rows.

CREATE TABLE murise.player_points (
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id       uuid NOT NULL REFERENCES murise.events (id),
    fixture_id     uuid NOT NULL,
    segment_index  integer NOT NULL CHECK (segment_index BETWEEN 0 AND 49),
    segment_label  text NOT NULL
        CHECK (btrim(segment_label) <> '' AND char_length(segment_label) <= 60),
    participant_id uuid NOT NULL,
    team_id        uuid NOT NULL,
    kind           text NOT NULL CHECK (kind IN ('award', 'reversal')),
    points         integer NOT NULL,
    reverses_id    uuid,
    actor_id       uuid NOT NULL REFERENCES murise.profiles (id),
    created_at     timestamptz NOT NULL DEFAULT now(),
    UNIQUE (event_id, id),
    UNIQUE (reverses_id),
    FOREIGN KEY (event_id, fixture_id)
        REFERENCES murise.fixtures (event_id, id),
    FOREIGN KEY (event_id, participant_id)
        REFERENCES murise.event_participants (event_id, id),
    FOREIGN KEY (event_id, team_id)
        REFERENCES murise.teams (event_id, id),
    FOREIGN KEY (event_id, reverses_id)
        REFERENCES murise.player_points (event_id, id),
    CHECK ((kind = 'award' AND points = 1 AND reverses_id IS NULL)
        OR (kind = 'reversal' AND points = -1 AND reverses_id IS NOT NULL))
);

CREATE INDEX ix_player_points_event_fixture
    ON murise.player_points (event_id, fixture_id);

CREATE INDEX ix_player_points_event_participant
    ON murise.player_points (event_id, participant_id);

CREATE TRIGGER protect_player_points
    BEFORE UPDATE OR DELETE OR TRUNCATE ON murise.player_points
    FOR EACH STATEMENT EXECUTE FUNCTION murise.reject_history_mutation();

ALTER TABLE murise.player_points ENABLE ROW LEVEL SECURITY;

REVOKE ALL ON TABLE murise.player_points
    FROM PUBLIC, anon, authenticated, murise_app;

GRANT SELECT, INSERT ON TABLE murise.player_points TO murise_app;

CREATE POLICY murise_app_runtime_access ON murise.player_points
    FOR ALL TO murise_app USING (true) WITH CHECK (true);
