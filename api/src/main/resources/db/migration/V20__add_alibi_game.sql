-- V20__add_alibi_game.sql
-- GM-046 / GM-020 / GM-028: playing Unified Alibi on phones.
--
-- One session per team fixture (each team plays in its own slot). A session has two
-- rounds, one per pair, played one after the other with a fresh case each.
-- A round: setup -> preparing (90 s, brief + fixed facts) -> questioning (10 x 30 s,
-- both partners answer on their own phone) -> judging (a judge compares locked
-- answers) -> done (every question revealed on the TV by an organizer).

CREATE TABLE murise.alibi_sessions (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id      uuid NOT NULL REFERENCES murise.events (id),
    event_game_id uuid NOT NULL,
    fixture_id    uuid NOT NULL,
    team_id       uuid NOT NULL,
    created_by    uuid NOT NULL REFERENCES murise.profiles (id),
    created_at    timestamptz NOT NULL DEFAULT now(),
    recorded_at   timestamptz,
    recorded_by   uuid REFERENCES murise.profiles (id),
    UNIQUE (event_id, id),
    UNIQUE (fixture_id),
    FOREIGN KEY (event_id, event_game_id) REFERENCES murise.event_games (event_id, id),
    FOREIGN KEY (event_id, fixture_id) REFERENCES murise.fixtures (event_id, id),
    FOREIGN KEY (event_id, team_id) REFERENCES murise.teams (event_id, id)
);

CREATE TABLE murise.alibi_rounds (
    id                   uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id             uuid NOT NULL,
    session_id           uuid NOT NULL,
    pair_number          integer NOT NULL CHECK (pair_number IN (1, 2)),
    player_a             uuid REFERENCES murise.profiles (id),
    player_b             uuid REFERENCES murise.profiles (id),
    case_id              uuid,
    case_version_id      uuid REFERENCES murise.alibi_case_versions (id),
    status               text NOT NULL DEFAULT 'setup'
        CHECK (status IN ('setup', 'preparing', 'questioning', 'judging', 'done')),
    prep_ends_at         timestamptz,
    current_question     integer NOT NULL DEFAULT 0 CHECK (current_question BETWEEN 0 AND 10),
    question_count       integer NOT NULL DEFAULT 10 CHECK (question_count BETWEEN 1 AND 10),
    revealed_count       integer NOT NULL DEFAULT 0 CHECK (revealed_count BETWEEN 0 AND 10),
    started_at           timestamptz,
    started_by           uuid REFERENCES murise.profiles (id),
    created_at           timestamptz NOT NULL DEFAULT now(),
    updated_at           timestamptz NOT NULL DEFAULT now(),
    UNIQUE (event_id, id),
    UNIQUE (session_id, pair_number),
    FOREIGN KEY (event_id, session_id) REFERENCES murise.alibi_sessions (event_id, id),
    FOREIGN KEY (event_id, case_id) REFERENCES murise.alibi_cases (event_id, id),
    CHECK (player_a IS NULL OR player_b IS NULL OR player_a <> player_b),
    -- Once a round has started, its pair and its exact case version are fixed.
    CHECK (status = 'setup'
        OR (player_a IS NOT NULL AND player_b IS NOT NULL AND case_version_id IS NOT NULL))
);

CREATE INDEX ix_alibi_rounds_event ON murise.alibi_rounds (event_id, status);

-- A case is played once per event: everyone in the room hears its reveal.
CREATE UNIQUE INDEX uq_alibi_rounds_case
    ON murise.alibi_rounds (event_id, case_id)
    WHERE case_id IS NOT NULL;

CREATE TABLE murise.alibi_prompts (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id         uuid NOT NULL,
    round_id         uuid NOT NULL,
    question_number  integer NOT NULL CHECK (question_number BETWEEN 1 AND 10),
    question_text    text NOT NULL CHECK (btrim(question_text) <> ''),
    opened_at        timestamptz NOT NULL,
    closes_at        timestamptz NOT NULL,
    compare_ready_at timestamptz,
    verdict          boolean,
    verdict_source   text CHECK (verdict_source IN ('auto', 'judge', 'correction')),
    decided_by       uuid REFERENCES murise.profiles (id),
    decided_at       timestamptz,
    revealed_at      timestamptz,
    UNIQUE (event_id, id),
    -- The same question ID is shared by both sides of the pair.
    UNIQUE (round_id, question_number),
    FOREIGN KEY (event_id, round_id) REFERENCES murise.alibi_rounds (event_id, id),
    CHECK (verdict IS NULL OR compare_ready_at IS NOT NULL),
    CHECK ((verdict IS NULL) = (verdict_source IS NULL)),
    CHECK (revealed_at IS NULL OR verdict IS NOT NULL)
);

CREATE TABLE murise.alibi_answers (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id    uuid NOT NULL,
    prompt_id   uuid NOT NULL,
    side        text NOT NULL CHECK (side IN ('A', 'B')),
    user_id     uuid NOT NULL REFERENCES murise.profiles (id),
    answer_text text CHECK (answer_text IS NULL OR char_length(answer_text) <= 300),
    -- No answer before the timer ran out: recorded explicitly, never as a blank match.
    missing     boolean NOT NULL DEFAULT false,
    away_ms     integer NOT NULL DEFAULT 0 CHECK (away_ms >= 0),
    away_count  integer NOT NULL DEFAULT 0 CHECK (away_count >= 0),
    locked_at   timestamptz NOT NULL DEFAULT now(),
    -- One locked answer per side: a second submission can never replace it.
    UNIQUE (prompt_id, side),
    FOREIGN KEY (event_id, prompt_id) REFERENCES murise.alibi_prompts (event_id, id),
    CHECK (NOT missing OR answer_text IS NULL)
);

CREATE TRIGGER set_updated_at
    BEFORE UPDATE ON murise.alibi_rounds
    FOR EACH ROW EXECUTE FUNCTION murise.touch_updated_at();

-- A locked answer is evidence: it can never be changed or deleted.
CREATE TRIGGER protect_alibi_answers
    BEFORE UPDATE OR DELETE OR TRUNCATE ON murise.alibi_answers
    FOR EACH STATEMENT
EXECUTE FUNCTION murise.reject_history_mutation();

ALTER TABLE murise.alibi_sessions ENABLE ROW LEVEL SECURITY;
ALTER TABLE murise.alibi_rounds ENABLE ROW LEVEL SECURITY;
ALTER TABLE murise.alibi_prompts ENABLE ROW LEVEL SECURITY;
ALTER TABLE murise.alibi_answers ENABLE ROW LEVEL SECURITY;

REVOKE ALL ON TABLE
    murise.alibi_sessions, murise.alibi_rounds, murise.alibi_prompts, murise.alibi_answers
    FROM PUBLIC, anon, authenticated, murise_app;

GRANT SELECT, INSERT, UPDATE ON TABLE
    murise.alibi_sessions, murise.alibi_rounds, murise.alibi_prompts
    TO murise_app;
GRANT SELECT, INSERT ON TABLE murise.alibi_answers TO murise_app;

CREATE POLICY murise_app_runtime_access ON murise.alibi_sessions
    FOR ALL TO murise_app USING (true) WITH CHECK (true);
CREATE POLICY murise_app_runtime_access ON murise.alibi_rounds
    FOR ALL TO murise_app USING (true) WITH CHECK (true);
CREATE POLICY murise_app_runtime_access ON murise.alibi_prompts
    FOR ALL TO murise_app USING (true) WITH CHECK (true);
CREATE POLICY murise_app_runtime_access ON murise.alibi_answers
    FOR ALL TO murise_app USING (true) WITH CHECK (true);
