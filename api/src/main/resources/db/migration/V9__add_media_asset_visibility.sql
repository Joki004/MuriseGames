-- V9__add_media_asset_visibility.sql
-- Adds visibility tracking to media_assets for private/unreleased event media.

ALTER TABLE murise.media_assets
    ADD COLUMN visibility text NOT NULL DEFAULT 'private';

ALTER TABLE murise.media_assets
    ADD CONSTRAINT ck_media_assets_visibility
        CHECK (visibility IN ('private', 'event_members', 'released'));

-- Existing picture assets are visible to authorized event members / sharers.
UPDATE murise.media_assets
SET visibility = 'event_members'
WHERE purpose = 'picture';