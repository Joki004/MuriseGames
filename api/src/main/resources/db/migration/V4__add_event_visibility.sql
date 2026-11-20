ALTER TABLE murise.events
    ADD COLUMN visibility text NOT NULL DEFAULT 'private';

ALTER TABLE murise.events
    ADD CONSTRAINT ck_events_visibility
        CHECK (visibility IN ('public', 'private'));

COMMENT ON COLUMN murise.events.visibility IS
    'Invite admission policy: public automatically admits authenticated '
    'invite holders as players; private requires organizer approval. '
    'Does not grant anonymous access or control scoreboard visibility.';