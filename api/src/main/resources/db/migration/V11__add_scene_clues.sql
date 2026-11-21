-- V11__add_scene_clues.sql
-- Clue authoring: drafts are organizer-only and read through the API.

CREATE TABLE murise.scene_entries (
    id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id   uuid NOT NULL REFERENCES murise.events (id),
    team_id    uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (event_id, id),
    UNIQUE (event_id, team_id),
    FOREIGN KEY (event_id, team_id) REFERENCES murise.teams (event_id, id)
);

CREATE TABLE murise.scene_clues (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id    uuid NOT NULL,
    scene_id    uuid NOT NULL,
    stage       integer NOT NULL CHECK (stage BETWEEN 1 AND 3),
    body        text NOT NULL CHECK (btrim(body) <> '' AND char_length(body) <= 280),
    approved    boolean NOT NULL DEFAULT false,
    approved_by uuid REFERENCES murise.profiles (id),
    approved_at timestamptz,
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now(),
    UNIQUE (event_id, id),
    UNIQUE (scene_id, stage),
    FOREIGN KEY (event_id, scene_id) REFERENCES murise.scene_entries (event_id, id),
    CHECK (NOT approved OR approved_by IS NOT NULL)
);

CREATE TRIGGER set_updated_at BEFORE UPDATE ON murise.scene_entries
    FOR EACH ROW EXECUTE FUNCTION murise.touch_updated_at();
CREATE TRIGGER set_updated_at BEFORE UPDATE ON murise.scene_clues
    FOR EACH ROW EXECUTE FUNCTION murise.touch_updated_at();

ALTER TABLE murise.scene_entries ENABLE ROW LEVEL SECURITY;
ALTER TABLE murise.scene_clues   ENABLE ROW LEVEL SECURITY;

REVOKE ALL ON TABLE murise.scene_entries, murise.scene_clues
    FROM PUBLIC, anon, authenticated, murise_app;
GRANT SELECT, INSERT, UPDATE, DELETE
    ON TABLE murise.scene_entries, murise.scene_clues TO murise_app;

CREATE POLICY murise_app_runtime_access ON murise.scene_entries
    FOR ALL TO murise_app USING (true) WITH CHECK (true);
CREATE POLICY murise_app_runtime_access ON murise.scene_clues
    FOR ALL TO murise_app USING (true) WITH CHECK (true);