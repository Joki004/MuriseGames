-- V19__add_offline_score_sync.sql
-- GM-022: Offline manual score sheet sync with case-insensitive row idempotency.

CREATE TABLE murise.offline_score_rows (
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id       uuid NOT NULL REFERENCES murise.events (id),
    batch_id       uuid NOT NULL,
    sheet_key      text NOT NULL
        CHECK (btrim(sheet_key) <> '' AND char_length(sheet_key) <= 60),
    row_number     integer NOT NULL CHECK (row_number >= 1),
    team_id        uuid NOT NULL,
    category       text NOT NULL CHECK (
        category IN ('game_points', 'bonus', 'penalty', 'adjustment')
    ),
    delta          numeric(12, 4) NOT NULL,
    reason         text NOT NULL
        CHECK (btrim(reason) <> '' AND char_length(reason) <= 220),
    score_entry_id uuid NOT NULL,
    synced_by      uuid NOT NULL REFERENCES murise.profiles (id),
    synced_at      timestamptz NOT NULL DEFAULT now(),
    UNIQUE (event_id, sheet_key, row_number),
    FOREIGN KEY (event_id, team_id)
        REFERENCES murise.teams (event_id, id),
    FOREIGN KEY (event_id, score_entry_id)
        REFERENCES murise.score_entries (event_id, id)
);

CREATE UNIQUE INDEX ux_offline_score_rows_normalized_sheet
    ON murise.offline_score_rows (event_id, lower(btrim(sheet_key)), row_number);

CREATE INDEX ix_offline_score_rows_event_batch
    ON murise.offline_score_rows (event_id, batch_id);

CREATE TRIGGER protect_offline_score_rows
    BEFORE UPDATE OR DELETE OR TRUNCATE ON murise.offline_score_rows
    FOR EACH STATEMENT EXECUTE FUNCTION murise.reject_history_mutation();

ALTER TABLE murise.offline_score_rows ENABLE ROW LEVEL SECURITY;

REVOKE ALL ON TABLE murise.offline_score_rows
    FROM PUBLIC, anon, authenticated, murise_app;

GRANT SELECT, INSERT ON TABLE murise.offline_score_rows TO murise_app;

CREATE POLICY murise_app_runtime_access ON murise.offline_score_rows
    FOR ALL TO murise_app USING (true) WITH CHECK (true);