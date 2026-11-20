DO $$
    DECLARE
        tbl RECORD;
        policy_name CONSTANT text := 'murise_app_runtime_access';
    BEGIN
        FOR tbl IN
            SELECT schemaname, tablename
            FROM pg_tables
            WHERE schemaname = 'murise'
              AND rowsecurity = true
              AND tablename <> 'flyway_schema_history'
            LOOP
                IF NOT EXISTS (
                    SELECT 1
                    FROM pg_policies
                    WHERE schemaname = tbl.schemaname
                      AND tablename = tbl.tablename
                      AND policyname = policy_name
                ) THEN
                    EXECUTE format(
                            'CREATE POLICY %I ON %I.%I FOR ALL TO murise_app USING (true) WITH CHECK (true)',
                            policy_name,
                            tbl.schemaname,
                            tbl.tablename
                            );
                END IF;
            END LOOP;
    END
$$;