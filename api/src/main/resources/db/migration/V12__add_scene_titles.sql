-- V12__add_scene_titles.sql
-- GM-012: private title registration and accepted aliases (organizer-only).

CREATE TABLE murise.scene_secrets (
    scene_id         uuid PRIMARY KEY,
    event_id         uuid NOT NULL,
    title            text NOT NULL
        CHECK (btrim(title) <> '' AND char_length(title) <= 120),
    normalized_title text NOT NULL CHECK (btrim(normalized_title) <> ''),
    answer_scope     text NOT NULL
        CHECK (answer_scope IN ('film', 'show', 'stage_play', 'franchise')),
    created_at       timestamptz NOT NULL DEFAULT now(),
    updated_at       timestamptz NOT NULL DEFAULT now(),
    UNIQUE (event_id, scene_id),
    FOREIGN KEY (event_id, scene_id)
        REFERENCES murise.scene_entries (event_id, id)
);

CREATE TABLE murise.title_aliases (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id         uuid NOT NULL,
    scene_id         uuid NOT NULL,
    alias            text NOT NULL
        CHECK (btrim(alias) <> '' AND char_length(alias) <= 120),
    normalized_alias text NOT NULL CHECK (btrim(normalized_alias) <> ''),
    created_at       timestamptz NOT NULL DEFAULT now(),
    UNIQUE (scene_id, normalized_alias),
    FOREIGN KEY (event_id, scene_id)
        REFERENCES murise.scene_entries (event_id, id)
);

CREATE INDEX ix_title_aliases_event ON murise.title_aliases (event_id, scene_id);

CREATE TRIGGER set_updated_at
    BEFORE UPDATE ON murise.scene_secrets
    FOR EACH ROW EXECUTE FUNCTION murise.touch_updated_at();

ALTER TABLE murise.scene_secrets ENABLE ROW LEVEL SECURITY;
ALTER TABLE murise.title_aliases ENABLE ROW LEVEL SECURITY;

REVOKE ALL ON TABLE murise.scene_secrets, murise.title_aliases
    FROM PUBLIC, anon, authenticated, murise_app;

GRANT SELECT, INSERT, UPDATE, DELETE
    ON TABLE murise.scene_secrets, murise.title_aliases
    TO murise_app;

CREATE POLICY murise_app_runtime_access ON murise.scene_secrets
    FOR ALL TO murise_app USING (true) WITH CHECK (true);
CREATE POLICY murise_app_runtime_access ON murise.title_aliases
    FOR ALL TO murise_app USING (true) WITH CHECK (true);