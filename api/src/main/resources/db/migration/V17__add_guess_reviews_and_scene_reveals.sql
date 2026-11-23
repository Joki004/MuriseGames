-- V17__add_guess_reviews_and_scene_reveals.sql
-- GM-016 (minimal): host decisions for guesses that don't match a title/alias exactly.
-- GM-018: host-commanded title reveals and separate ledger categories for guessing.

-- Host verdicts. Kept apart from murise.guesses so player-readable rows never carry a verdict.
-- A decision can change until that scene is revealed (enforced in FinaleService);
-- every change is written to audit_events.
CREATE TABLE murise.guess_reviews (
    guess_id    uuid PRIMARY KEY REFERENCES murise.guesses (id),
    event_id    uuid NOT NULL REFERENCES murise.events (id),
    verdict     text NOT NULL CHECK (verdict IN ('correct', 'wrong')),
    decided_by  uuid NOT NULL REFERENCES murise.profiles (id),
    decided_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX ix_guess_reviews_event ON murise.guess_reviews (event_id);

CREATE TRIGGER set_updated_at
    BEFORE UPDATE ON murise.guess_reviews
    FOR EACH ROW EXECUTE FUNCTION murise.touch_updated_at();

-- One row per revealed title. Append-only: a reveal cannot be undone or repeated.
CREATE TABLE murise.scene_reveals (
    scene_id    uuid PRIMARY KEY,
    event_id    uuid NOT NULL REFERENCES murise.events (id),
    request_id  uuid NOT NULL,
    revealed_by uuid NOT NULL REFERENCES murise.profiles (id),
    revealed_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (event_id, request_id),
    FOREIGN KEY (event_id, scene_id)
        REFERENCES murise.scene_entries (event_id, id)
);

CREATE TRIGGER protect_scene_reveals
    BEFORE UPDATE OR DELETE OR TRUNCATE ON murise.scene_reveals
    FOR EACH STATEMENT EXECUTE FUNCTION murise.reject_history_mutation();

-- Ledger: guessing points and performer bonuses are their own categories (GM-010, GM-052).
DO $$
DECLARE
    constraint_name text;
BEGIN
    SELECT conname INTO constraint_name
    FROM pg_constraint
    WHERE conrelid = 'murise.score_entries'::regclass
      AND contype = 'c'
      AND pg_get_constraintdef(oid) LIKE '%game_points%';

    IF constraint_name IS NOT NULL THEN
        EXECUTE format('ALTER TABLE murise.score_entries DROP CONSTRAINT %I', constraint_name);
    END IF;
END
$$;

ALTER TABLE murise.score_entries
    ADD CONSTRAINT score_entries_category_check CHECK (
        category IN ('game_points', 'bonus', 'penalty', 'adjustment', 'reversal',
                     'guess_points', 'performer_bonus')
    );

-- Which guess produced a guessing/performer entry (planned in V2 "with Scene mechanics").
ALTER TABLE murise.score_entries
    ADD COLUMN guess_id uuid REFERENCES murise.guesses (id);

-- Permissions, same pattern as the other private Scene tables.
ALTER TABLE murise.guess_reviews ENABLE ROW LEVEL SECURITY;
ALTER TABLE murise.scene_reveals ENABLE ROW LEVEL SECURITY;

REVOKE ALL ON TABLE murise.guess_reviews, murise.scene_reveals
    FROM PUBLIC, anon, authenticated, murise_app;

GRANT SELECT, INSERT, UPDATE ON TABLE murise.guess_reviews TO murise_app;
GRANT SELECT, INSERT         ON TABLE murise.scene_reveals TO murise_app;

CREATE POLICY murise_app_runtime_access ON murise.guess_reviews
    FOR ALL TO murise_app USING (true) WITH CHECK (true);
CREATE POLICY murise_app_runtime_access ON murise.scene_reveals
    FOR ALL TO murise_app USING (true) WITH CHECK (true);
