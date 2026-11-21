CREATE TABLE murise.invite_redemption_limits (
                                                 user_id           uuid PRIMARY KEY,
                                                 window_started_at timestamptz NOT NULL,
                                                 attempts          integer NOT NULL CHECK (attempts BETWEEN 1 AND 11)
);

-- This operational table uses the verified JWT subject.
-- It intentionally does not require an application profile to exist yet.

REVOKE ALL ON TABLE murise.invite_redemption_limits
    FROM PUBLIC, anon, authenticated;

GRANT SELECT, INSERT, UPDATE
    ON TABLE murise.invite_redemption_limits
    TO murise_app;

ALTER TABLE murise.invite_redemption_limits ENABLE ROW LEVEL SECURITY;

CREATE POLICY murise_app_runtime_access
    ON murise.invite_redemption_limits
    FOR ALL
    TO murise_app
    USING (true)
    WITH CHECK (true);