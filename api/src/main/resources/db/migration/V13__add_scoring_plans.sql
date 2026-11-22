CREATE TABLE murise.scoring_plans (
                                      event_id          uuid PRIMARY KEY REFERENCES murise.events(id),
                                      schedule_id       uuid NOT NULL,
                                      request_id        uuid NOT NULL,
                                      request_hash      text NOT NULL
                                          CHECK (request_hash ~ '^[0-9a-f]{64}$'),
                                      approved_by       uuid NOT NULL REFERENCES murise.profiles(id),
                                      decision_note     text NOT NULL
                                          CHECK (char_length(btrim(decision_note)) BETWEEN 20 AND 2000),
                                      snapshot          jsonb NOT NULL
                                          CHECK (
                                              jsonb_typeof(snapshot) = 'object'
                                                  AND snapshot ? 'policy'
                                                  AND snapshot ->> 'policy' = 'A'
                                              ),
                                      created_at        timestamptz NOT NULL DEFAULT now(),
                                      FOREIGN KEY (event_id, schedule_id)
                                          REFERENCES murise.schedule_versions(event_id, id)
);

ALTER TABLE murise.scoring_plans ENABLE ROW LEVEL SECURITY;

REVOKE ALL ON TABLE murise.scoring_plans
    FROM PUBLIC, anon, authenticated, murise_app;

GRANT SELECT, INSERT ON TABLE murise.scoring_plans TO murise_app;

CREATE POLICY murise_app_runtime_access ON murise.scoring_plans
    FOR ALL TO murise_app USING (true) WITH CHECK (true);

CREATE TRIGGER protect_scoring_plans
    BEFORE UPDATE OR DELETE OR TRUNCATE ON murise.scoring_plans
    FOR EACH STATEMENT
EXECUTE FUNCTION murise.reject_history_mutation();


-- The schedule and its scoring plan must be published together.
CREATE FUNCTION murise.validate_scoring_plan_insert()
    RETURNS trigger
    LANGUAGE plpgsql
    SET search_path = pg_catalog
AS $$
BEGIN
    PERFORM 1
    FROM murise.events
    WHERE id = NEW.event_id
        FOR UPDATE;

    IF NOT EXISTS (
        SELECT 1
        FROM murise.schedule_versions
        WHERE event_id = NEW.event_id
          AND id = NEW.schedule_id
          AND status = 'published'
    ) THEN
        RAISE EXCEPTION 'Publish the schedule before freezing its scoring plan'
            USING ERRCODE = '23514';
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER validate_scoring_plan_insert
    BEFORE INSERT ON murise.scoring_plans
    FOR EACH ROW
EXECUTE FUNCTION murise.validate_scoring_plan_insert();


-- All relevant mutations share the event lock with review/publication.
-- After publication, protect the sources of frozen eligibility.
CREATE FUNCTION murise.guard_scoring_plan_sources()
    RETURNS trigger
    LANGUAGE plpgsql
    SET search_path = pg_catalog
AS $$
DECLARE
    n jsonb := '{}'::jsonb;
    o jsonb := '{}'::jsonb;
    d jsonb;
    event_uuid uuid;
    plan murise.scoring_plans%ROWTYPE;
    protected_row boolean := false;
BEGIN
    IF TG_OP <> 'DELETE' THEN n := to_jsonb(NEW); END IF;
    IF TG_OP <> 'INSERT' THEN o := to_jsonb(OLD); END IF;

    d := CASE WHEN TG_OP = 'DELETE' THEN o ELSE n END;
    event_uuid := (d ->> 'event_id')::uuid;

    IF TG_OP = 'UPDATE'
        AND n ->> 'event_id' IS DISTINCT FROM o ->> 'event_id'
    THEN
        RAISE EXCEPTION 'An existing record cannot move to another event'
            USING ERRCODE = '23514';
    END IF;

    PERFORM 1
    FROM murise.events
    WHERE id = event_uuid
        FOR UPDATE;

    SELECT * INTO plan
    FROM murise.scoring_plans
    WHERE event_id = event_uuid;

    IF FOUND THEN
        IF TG_TABLE_NAME = 'teams' THEN
            IF TG_OP = 'INSERT' THEN
                protected_row := (n ->> 'active')::boolean;
            ELSIF TG_OP = 'DELETE' THEN
                protected_row := EXISTS (
                    SELECT 1
                    FROM jsonb_array_elements(plan.snapshot -> 'teams') t
                    WHERE t ->> 'id' = o ->> 'id'
                );
            ELSE
                -- Names, colors and display order may still change.
                protected_row :=
                        (n - 'name' - 'color' - 'sort_order' - 'updated_at')
                            IS DISTINCT FROM
                            (o - 'name' - 'color' - 'sort_order' - 'updated_at');
            END IF;

        ELSIF TG_TABLE_NAME = 'event_games' THEN
            IF EXISTS (
                SELECT 1
                FROM jsonb_array_elements(plan.snapshot -> 'games') g
                WHERE g ->> 'eventGameId' IN (n ->> 'id', o ->> 'id')
            ) THEN
                protected_row := TG_OP <> 'UPDATE';

                IF TG_OP = 'UPDATE' THEN
                    -- Runtime state and visibility do not change eligibility.
                    protected_row :=
                            (n - 'status' - 'sort_order'
                                - 'scoreboard_visibility_override' - 'updated_at')
                                IS DISTINCT FROM
                                (o - 'status' - 'sort_order'
                                    - 'scoreboard_visibility_override' - 'updated_at');
                END IF;
            END IF;

        ELSIF TG_TABLE_NAME = 'schedule_versions' THEN
            protected_row :=
                    plan.schedule_id::text IN (n ->> 'id', o ->> 'id');

        ELSIF TG_TABLE_NAME IN ('schedule_slots', 'fixtures') THEN
            protected_row := plan.schedule_id::text IN (
                                                        n ->> 'schedule_version_id',
                                                        o ->> 'schedule_version_id'
                );

            IF protected_row
                AND TG_TABLE_NAME = 'fixtures'
                AND TG_OP = 'UPDATE'
            THEN
                -- A cancellation may change runtime status, never the
                -- frozen opponents, game, slot or scoring opportunity.
                protected_row :=
                        (n - 'status' - 'updated_at')
                            IS DISTINCT FROM
                            (o - 'status' - 'updated_at');
            END IF;

        ELSIF TG_TABLE_NAME = 'fixture_teams' THEN
            protected_row := EXISTS (
                SELECT 1
                FROM murise.fixtures f
                WHERE f.event_id = event_uuid
                  AND f.schedule_version_id = plan.schedule_id
                  AND f.id::text IN (
                                     n ->> 'fixture_id',
                                     o ->> 'fixture_id'
                    )
            );

        ELSIF TG_TABLE_NAME = 'fixture_dependencies' THEN
            protected_row := EXISTS (
                SELECT 1
                FROM murise.fixtures f
                WHERE f.event_id = event_uuid
                  AND f.schedule_version_id = plan.schedule_id
                  AND f.id::text IN (
                                     n ->> 'source_fixture_id',
                                     o ->> 'source_fixture_id',
                                     n ->> 'target_fixture_id',
                                     o ->> 'target_fixture_id'
                    )
            );
        END IF;

        IF protected_row THEN
            RAISE EXCEPTION
                'The published scoring plan locks teams, rules and schedule opportunities'
                USING ERRCODE = '23514';
        END IF;
    END IF;

    IF TG_OP = 'DELETE' THEN RETURN OLD; END IF;
    RETURN NEW;
END;
$$;

DO $$
    DECLARE
        table_name text;
    BEGIN
        FOREACH table_name IN ARRAY ARRAY[
            'teams',
            'event_games',
            'schedule_versions',
            'schedule_slots',
            'fixtures',
            'fixture_teams',
            'fixture_dependencies',
            'rounds',
            'round_results',
            'fixture_results',
            'game_results',
            'score_entries',
            'event_memberships',
            'event_role_grants'
            ]
            LOOP
                EXECUTE format(
                        'CREATE TRIGGER guard_scoring_plan_sources
                         BEFORE INSERT OR UPDATE OR DELETE ON murise.%I
                         FOR EACH ROW
                         EXECUTE FUNCTION murise.guard_scoring_plan_sources()',
                        table_name
                        );
            END LOOP;
    END;
$$;

REVOKE ALL ON FUNCTION murise.validate_scoring_plan_insert()
    FROM PUBLIC, anon, authenticated;

REVOKE ALL ON FUNCTION murise.guard_scoring_plan_sources()
    FROM PUBLIC, anon, authenticated;