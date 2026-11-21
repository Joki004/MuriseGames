-- Existing event media remains event-scoped.
-- Picture assets may also belong to a user without an event.

ALTER TABLE murise.media_assets
    ADD COLUMN purpose text NOT NULL DEFAULT 'event_media',
    ALTER COLUMN event_id DROP NOT NULL,
    DROP CONSTRAINT media_assets_status_check;

ALTER TABLE murise.media_assets
    ADD CONSTRAINT ck_media_assets_purpose
        CHECK (purpose IN ('event_media', 'picture')),

    ADD CONSTRAINT ck_media_assets_event_scope
        CHECK (purpose = 'picture' OR event_id IS NOT NULL),

    ADD CONSTRAINT media_assets_status_check
        CHECK (
            status IN (
                       'pending',
                       'ready',
                       'failed',
                       'delete_pending',
                       'deleted'
                )
            ),

    ADD CONSTRAINT uq_media_assets_owner_id
        UNIQUE (owner_id, id);

CREATE TABLE murise.pictures (
                                 id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
                                 slot        text NOT NULL,

                                 user_id     uuid REFERENCES murise.profiles(id),
                                 event_id    uuid REFERENCES murise.events(id),
                                 team_id     uuid,

                                 emoji       text,
                                 asset_id    uuid REFERENCES murise.media_assets(id),

                                 created_at  timestamptz NOT NULL DEFAULT now(),
                                 updated_at  timestamptz NOT NULL DEFAULT now(),

                                 CONSTRAINT ck_picture_slot
                                     CHECK (
                                         slot IN (
                                                  'AVATAR',
                                                  'EVENT_ICON',
                                                  'EVENT_COVER',
                                                  'TEAM_ICON'
                                             )
                                         ),

                                 CONSTRAINT ck_picture_target
                                     CHECK (
                                         (
                                             slot = 'AVATAR'
                                                 AND user_id IS NOT NULL
                                                 AND event_id IS NULL
                                                 AND team_id IS NULL
                                             )
                                             OR
                                         (
                                             slot IN ('EVENT_ICON', 'EVENT_COVER')
                                                 AND user_id IS NULL
                                                 AND event_id IS NOT NULL
                                                 AND team_id IS NULL
                                             )
                                             OR
                                         (
                                             slot = 'TEAM_ICON'
                                                 AND user_id IS NULL
                                                 AND event_id IS NOT NULL
                                                 AND team_id IS NOT NULL
                                             )
                                         ),

    -- Both null means "use the default appearance".
                                 CONSTRAINT ck_picture_selection
                                     CHECK (emoji IS NULL OR asset_id IS NULL),

                                 CONSTRAINT ck_picture_emoji
                                     CHECK (
                                         emoji IS NULL
                                             OR (
                                             char_length(emoji) BETWEEN 1 AND 32
                                                 AND slot <> 'EVENT_COVER'
                                             )
                                         ),

                                 CONSTRAINT fk_picture_team
                                     FOREIGN KEY (event_id, team_id)
                                         REFERENCES murise.teams(event_id, id),

    -- An avatar cannot reference another user's uploaded asset.
                                 CONSTRAINT fk_picture_avatar_asset
                                     FOREIGN KEY (user_id, asset_id)
                                         REFERENCES murise.media_assets(owner_id, id),

    -- Event/team pictures cannot reference another event's asset.
                                 CONSTRAINT fk_picture_event_asset
                                     FOREIGN KEY (event_id, asset_id)
                                         REFERENCES murise.media_assets(event_id, id),

                                 UNIQUE (user_id),
                                 UNIQUE (team_id),
                                 UNIQUE (asset_id)
);

CREATE UNIQUE INDEX uq_picture_event_slot
    ON murise.pictures(event_id, slot)
    WHERE team_id IS NULL AND event_id IS NOT NULL;

CREATE INDEX ix_picture_asset_cleanup
    ON murise.media_assets(status, created_at)
    WHERE purpose = 'picture';

CREATE TRIGGER set_updated_at
    BEFORE UPDATE ON murise.pictures
    FOR EACH ROW
EXECUTE FUNCTION murise.touch_updated_at();

ALTER TABLE murise.pictures ENABLE ROW LEVEL SECURITY;

REVOKE ALL ON TABLE murise.pictures
    FROM PUBLIC, anon, authenticated, murise_app;

GRANT SELECT, INSERT, UPDATE ON TABLE murise.pictures
    TO murise_app;

CREATE POLICY murise_app_runtime_access
    ON murise.pictures
    FOR ALL
    TO murise_app
    USING (true)
    WITH CHECK (true);