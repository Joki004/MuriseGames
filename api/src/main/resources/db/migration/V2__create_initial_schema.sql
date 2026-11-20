-- V2__create_initial_schema.sql
--
-- Requires the existing murise schema and database roles.
-- Execute through Flyway as murise_migrator.
--
-- Supabase owns auth and storage. Do not recreate those schemas.
-- Flyway manages the migration transaction.
--
-- Application authorization and business transactions remain in Spring Boot.

-- ============================================================
-- 1. Profiles and platform permissions
-- ============================================================

-- murise.profiles is provisioned separately as postgres
-- because it references the Supabase-managed auth.users table.

CREATE TABLE murise.platform_role_grants (
                                             id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
                                             user_id    uuid NOT NULL REFERENCES murise.profiles (id),
                                             role       text NOT NULL CHECK (role = 'ADMIN'),
                                             granted_by uuid REFERENCES murise.profiles (id),
                                             created_at timestamptz NOT NULL DEFAULT now(),
                                             revoked_at timestamptz,
                                             revoked_by uuid REFERENCES murise.profiles (id),
                                             CHECK (revoked_at IS NULL OR revoked_at >= created_at),
                                             CHECK (revoked_by IS NULL OR revoked_at IS NOT NULL)
);

CREATE UNIQUE INDEX uq_platform_role_active
    ON murise.platform_role_grants (user_id, role)
    WHERE revoked_at IS NULL;

-- ============================================================
-- 2. Shared game catalog and immutable published versions
-- ============================================================

CREATE TABLE murise.game_definitions (
                                         id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
                                         code       text NOT NULL UNIQUE CHECK (btrim(code) <> ''),
                                         name       text NOT NULL CHECK (btrim(name) <> ''),
                                         kind       text NOT NULL CHECK (
                                             kind IN ('competitive', 'party', 'social_deduction', 'rehearsal')
                                             ),
                                         active     boolean NOT NULL DEFAULT true,
                                         created_at timestamptz NOT NULL DEFAULT now(),
                                         updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE murise.game_versions (
                                      id                       uuid PRIMARY KEY DEFAULT gen_random_uuid(),
                                      game_definition_id       uuid NOT NULL
                                          REFERENCES murise.game_definitions (id),
                                      version                  integer NOT NULL CHECK (version > 0),
                                      summary                  text NOT NULL,
                                      rules_markdown           text NOT NULL,
                                      end_game_scoring_summary  text NOT NULL,
                                      participation_mode       text NOT NULL CHECK (
                                          participation_mode IN (
                                                                 'head_to_head', 'team_solo', 'all_teams',
                                                                 'multi_team_pod', 'individual'
                                              )
                                          ),
                                      scoring_handler          text NOT NULL CHECK (btrim(scoring_handler) <> ''),
                                      scoring_config           jsonb NOT NULL DEFAULT '{}'::jsonb,
                                      settings_schema          jsonb NOT NULL DEFAULT '{}'::jsonb,
                                      result_schema            jsonb NOT NULL DEFAULT '{}'::jsonb,
                                      status                   text NOT NULL DEFAULT 'draft'
                                          CHECK (status IN ('draft', 'published', 'archived')),
                                      source_ref               text,
                                      created_at               timestamptz NOT NULL DEFAULT now(),
                                      updated_at               timestamptz NOT NULL DEFAULT now(),
                                      UNIQUE (game_definition_id, version),
                                      UNIQUE (game_definition_id, id),
                                      CHECK (jsonb_typeof(scoring_config) = 'object'),
                                      CHECK (jsonb_typeof(settings_schema) = 'object'),
                                      CHECK (jsonb_typeof(result_schema) = 'object')
);

-- ============================================================
-- 3. Events, memberships and event permissions
-- ============================================================

CREATE TABLE murise.events (
                               id                    uuid PRIMARY KEY DEFAULT gen_random_uuid(),
                               name                  text NOT NULL CHECK (btrim(name) <> ''),
                               theme                 text,
                               starts_at             timestamptz NOT NULL,
                               timezone              text NOT NULL CHECK (btrim(timezone) <> ''),
                               venue                 text,
                               status                text NOT NULL DEFAULT 'draft' CHECK (
                                   status IN ('draft', 'published', 'live', 'completed', 'archived')
                                   ),
                               scoreboard_visibility text NOT NULL DEFAULT 'hidden'
                                   CHECK (
                                       scoreboard_visibility IN (
                                                                 'public', 'hidden', 'frozen'
                                           )
                                       ),
                               created_by            uuid NOT NULL REFERENCES murise.profiles (id),
                               created_at            timestamptz NOT NULL DEFAULT now(),
                               updated_at            timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE murise.event_memberships (
                                          id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
                                          event_id   uuid NOT NULL REFERENCES murise.events (id),
                                          user_id    uuid NOT NULL REFERENCES murise.profiles (id),
                                          status     text NOT NULL DEFAULT 'active'
                                              CHECK (status IN ('active', 'suspended', 'revoked')),
                                          created_at timestamptz NOT NULL DEFAULT now(),
                                          updated_at timestamptz NOT NULL DEFAULT now(),
                                          UNIQUE (event_id, id),
                                          UNIQUE (event_id, user_id)
);

CREATE INDEX ix_memberships_user
    ON murise.event_memberships (user_id);

CREATE TABLE murise.event_role_grants (
                                          id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
                                          event_id      uuid NOT NULL REFERENCES murise.events (id),
                                          membership_id uuid NOT NULL,
                                          role          text NOT NULL
                                              CHECK (role IN ('ORGANIZER', 'REFEREE', 'PLAYER', 'VIEWER')),
                                          granted_by    uuid NOT NULL REFERENCES murise.profiles (id),
                                          created_at    timestamptz NOT NULL DEFAULT now(),
                                          revoked_at    timestamptz,
                                          revoked_by    uuid REFERENCES murise.profiles (id),
                                          UNIQUE (event_id, id),
                                          FOREIGN KEY (event_id, membership_id)
                                              REFERENCES murise.event_memberships (event_id, id),
                                          CHECK (revoked_at IS NULL OR revoked_at >= created_at),
                                          CHECK (revoked_by IS NULL OR revoked_at IS NOT NULL)
);

CREATE UNIQUE INDEX uq_event_role_active
    ON murise.event_role_grants (event_id, membership_id, role)
    WHERE revoked_at IS NULL;

CREATE INDEX ix_event_roles_membership
    ON murise.event_role_grants (event_id, membership_id);

-- ============================================================
-- 4. Invitations and joining
-- ============================================================

CREATE TABLE murise.event_invites (
                                      id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
                                      event_id   uuid NOT NULL REFERENCES murise.events (id),
                                      token_hash text NOT NULL UNIQUE CHECK (btrim(token_hash) <> ''),
                                      expires_at timestamptz NOT NULL,
                                      revoked_at timestamptz,
                                      max_uses   integer,
                                      use_count  integer NOT NULL DEFAULT 0,
                                      created_by uuid NOT NULL REFERENCES murise.profiles (id),
                                      created_at timestamptz NOT NULL DEFAULT now(),
                                      UNIQUE (event_id, id),
                                      CHECK (max_uses IS NULL OR max_uses > 0),
                                      CHECK (use_count >= 0),
                                      CHECK (max_uses IS NULL OR use_count <= max_uses),
                                      CHECK (expires_at > created_at),
                                      CHECK (revoked_at IS NULL OR revoked_at >= created_at)
);

CREATE TABLE murise.join_requests (
                                      id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
                                      event_id   uuid NOT NULL REFERENCES murise.events (id),
                                      user_id    uuid NOT NULL REFERENCES murise.profiles (id),
                                      invite_id  uuid,
                                      status     text NOT NULL DEFAULT 'pending'
                                          CHECK (status IN ('pending', 'approved', 'rejected', 'cancelled')),
                                      decided_by uuid REFERENCES murise.profiles (id),
                                      decided_at timestamptz,
                                      created_at timestamptz NOT NULL DEFAULT now(),
                                      updated_at timestamptz NOT NULL DEFAULT now(),
                                      UNIQUE (event_id, id),
                                      FOREIGN KEY (event_id, invite_id)
                                          REFERENCES murise.event_invites (event_id, id),
                                      CHECK (
                                          status NOT IN ('approved', 'rejected')
                                              OR (decided_by IS NOT NULL AND decided_at IS NOT NULL)
                                          )
);

CREATE UNIQUE INDEX uq_join_request_pending
    ON murise.join_requests (event_id, user_id)
    WHERE status = 'pending';

CREATE INDEX ix_join_requests_user
    ON murise.join_requests (user_id);

CREATE INDEX ix_join_requests_invite
    ON murise.join_requests (event_id, invite_id);

-- ============================================================
-- 5. Teams and participants
-- ============================================================

CREATE TABLE murise.teams (
                              id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
                              event_id   uuid NOT NULL REFERENCES murise.events (id),
                              name       text NOT NULL CHECK (btrim(name) <> ''),
                              color      text NOT NULL,
                              sort_order integer NOT NULL DEFAULT 0 CHECK (sort_order >= 0),
                              active     boolean NOT NULL DEFAULT true,
                              created_at timestamptz NOT NULL DEFAULT now(),
                              updated_at timestamptz NOT NULL DEFAULT now(),
                              UNIQUE (event_id, id),
                              UNIQUE (event_id, name)
);

CREATE TABLE murise.event_participants (
                                           id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
                                           event_id     uuid NOT NULL REFERENCES murise.events (id),
                                           user_id      uuid,
                                           display_name text NOT NULL CHECK (btrim(display_name) <> ''),
                                           team_id      uuid,
                                           status       text NOT NULL DEFAULT 'invited'
                                               CHECK (status IN ('invited', 'active', 'benched', 'removed')),
                                           is_captain   boolean NOT NULL DEFAULT false,
                                           created_at   timestamptz NOT NULL DEFAULT now(),
                                           updated_at   timestamptz NOT NULL DEFAULT now(),
                                           UNIQUE (event_id, id),
                                           UNIQUE (event_id, user_id),
                                           FOREIGN KEY (event_id, user_id)
                                               REFERENCES murise.event_memberships (event_id, user_id),
                                           FOREIGN KEY (event_id, team_id)
                                               REFERENCES murise.teams (event_id, id),
                                           CHECK (NOT is_captain OR team_id IS NOT NULL)
);

CREATE UNIQUE INDEX uq_active_team_captain
    ON murise.event_participants (event_id, team_id)
    WHERE is_captain AND status = 'active';

CREATE INDEX ix_participants_team
    ON murise.event_participants (event_id, team_id);

-- ============================================================
-- 6. Event lineup
-- ============================================================

CREATE TABLE murise.event_games (
                                    id                             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
                                    event_id                       uuid NOT NULL REFERENCES murise.events (id),
                                    game_definition_id             uuid NOT NULL,
                                    game_version_id                uuid NOT NULL,
                                    settings_snapshot              jsonb NOT NULL DEFAULT '{}'::jsonb,
                                    scoreboard_visibility_override text
                                        CHECK (
                                            scoreboard_visibility_override
                                                IN ('public', 'hidden')
                                            ),
                                    house_rules_note                text,
                                    status                         text NOT NULL DEFAULT 'scheduled'
                                        CHECK (
                                            status IN (
                                                       'scheduled', 'active',
                                                       'settled', 'voided'
                                                )
                                            ),
                                    sort_order                     integer NOT NULL DEFAULT 0
                                        CHECK (sort_order >= 0),
                                    created_at                     timestamptz NOT NULL DEFAULT now(),
                                    updated_at                     timestamptz NOT NULL DEFAULT now(),
                                    UNIQUE (event_id, id),
                                    UNIQUE (event_id, game_definition_id),
                                    FOREIGN KEY (game_definition_id, game_version_id)
                                        REFERENCES murise.game_versions (game_definition_id, id),
                                    CHECK (jsonb_typeof(settings_snapshot) = 'object')
);

CREATE INDEX ix_event_games_version
    ON murise.event_games (game_definition_id, game_version_id);

-- ============================================================
-- 7. Schedules and resources
-- ============================================================

CREATE TABLE murise.schedule_versions (
                                          id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
                                          event_id          uuid NOT NULL REFERENCES murise.events (id),
                                          mode              text NOT NULL
                                              CHECK (mode IN ('balanced', 'round_robin', 'manual')),
                                          algorithm_version text NOT NULL,
                                          seed              bigint,
                                          config            jsonb NOT NULL DEFAULT '{}'::jsonb,
                                          status            text NOT NULL DEFAULT 'draft'
                                              CHECK (status IN ('draft', 'published', 'superseded')),
                                          version           integer NOT NULL CHECK (version > 0),
                                          created_at        timestamptz NOT NULL DEFAULT now(),
                                          updated_at        timestamptz NOT NULL DEFAULT now(),
                                          UNIQUE (event_id, id),
                                          UNIQUE (event_id, version),
                                          CHECK (jsonb_typeof(config) = 'object')
);

CREATE UNIQUE INDEX uq_published_schedule
    ON murise.schedule_versions (event_id)
    WHERE status = 'published';

CREATE TABLE murise.schedule_slots (
                                       id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
                                       event_id            uuid NOT NULL REFERENCES murise.events (id),
                                       schedule_version_id uuid NOT NULL,
                                       event_game_id       uuid NOT NULL,
                                       start_at            timestamptz NOT NULL,
                                       end_at              timestamptz NOT NULL,
                                       created_at          timestamptz NOT NULL DEFAULT now(),
                                       updated_at          timestamptz NOT NULL DEFAULT now(),
                                       UNIQUE (event_id, id),
                                       UNIQUE (event_id, schedule_version_id, event_game_id, id),
                                       FOREIGN KEY (event_id, schedule_version_id)
                                           REFERENCES murise.schedule_versions (event_id, id),
                                       FOREIGN KEY (event_id, event_game_id)
                                           REFERENCES murise.event_games (event_id, id),
                                       CHECK (end_at > start_at)
);

CREATE INDEX ix_slots_game
    ON murise.schedule_slots (event_id, event_game_id);

CREATE INDEX ix_slots_start
    ON murise.schedule_slots (event_id, schedule_version_id, start_at);

CREATE TABLE murise.event_resources (
                                        id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
                                        event_id   uuid NOT NULL REFERENCES murise.events (id),
                                        type       text NOT NULL CHECK (type IN ('room', 'table', 'station')),
                                        name       text NOT NULL CHECK (btrim(name) <> ''),
                                        capacity   integer NOT NULL CHECK (capacity > 0),
                                        created_at timestamptz NOT NULL DEFAULT now(),
                                        updated_at timestamptz NOT NULL DEFAULT now(),
                                        UNIQUE (event_id, id)
);

CREATE TABLE murise.slot_resource_assignments (
                                                  event_id   uuid NOT NULL,
                                                  slot_id    uuid NOT NULL,
                                                  resource_id uuid NOT NULL,
                                                  PRIMARY KEY (slot_id, resource_id),
                                                  FOREIGN KEY (event_id, slot_id)
                                                      REFERENCES murise.schedule_slots (event_id, id),
                                                  FOREIGN KEY (event_id, resource_id)
                                                      REFERENCES murise.event_resources (event_id, id)
);

CREATE INDEX ix_slot_resources_resource
    ON murise.slot_resource_assignments (event_id, resource_id);

-- ============================================================
-- 8. Fixtures, participation and rounds
-- ============================================================

CREATE TABLE murise.fixtures (
                                 id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
                                 event_id            uuid NOT NULL REFERENCES murise.events (id),
                                 event_game_id       uuid NOT NULL,
                                 schedule_version_id uuid NOT NULL,
                                 slot_id             uuid NOT NULL,
                                 round_number        integer NOT NULL CHECK (round_number > 0),
                                 status              text NOT NULL DEFAULT 'scheduled'
                                     CHECK (
                                         status IN (
                                                    'scheduled', 'active', 'completed', 'voided'
                                             )
                                         ),
                                 created_at          timestamptz NOT NULL DEFAULT now(),
                                 updated_at          timestamptz NOT NULL DEFAULT now(),
                                 UNIQUE (event_id, id),
                                 FOREIGN KEY (event_id, event_game_id)
                                     REFERENCES murise.event_games (event_id, id),
                                 FOREIGN KEY (event_id, schedule_version_id)
                                     REFERENCES murise.schedule_versions (event_id, id),
                                 FOREIGN KEY (event_id, schedule_version_id, event_game_id, slot_id)
                                     REFERENCES murise.schedule_slots (
                                                                       event_id, schedule_version_id, event_game_id, id
                                         )
);

CREATE INDEX ix_fixtures_game
    ON murise.fixtures (event_id, event_game_id);

CREATE INDEX ix_fixtures_slot
    ON murise.fixtures (
                        event_id, schedule_version_id, event_game_id, slot_id
        );

CREATE TABLE murise.fixture_teams (
                                      event_id   uuid NOT NULL,
                                      fixture_id uuid NOT NULL,
                                      team_id    uuid NOT NULL,
                                      side       text NOT NULL CHECK (btrim(side) <> ''),
                                      created_at timestamptz NOT NULL DEFAULT now(),
                                      PRIMARY KEY (fixture_id, team_id),
                                      UNIQUE (event_id, fixture_id, team_id),
                                      FOREIGN KEY (event_id, fixture_id)
                                          REFERENCES murise.fixtures (event_id, id),
                                      FOREIGN KEY (event_id, team_id)
                                          REFERENCES murise.teams (event_id, id)
);

CREATE INDEX ix_fixture_teams_team
    ON murise.fixture_teams (event_id, team_id);

-- Reserved structure only; tournament generation remains deferred.
CREATE TABLE murise.fixture_dependencies (
                                             event_id          uuid NOT NULL,
                                             source_fixture_id uuid NOT NULL,
                                             target_fixture_id uuid NOT NULL,
                                             target_side       text NOT NULL CHECK (btrim(target_side) <> ''),
                                             qualification     text NOT NULL CHECK (btrim(qualification) <> ''),
                                             PRIMARY KEY (
                                                          event_id, source_fixture_id, target_fixture_id,
                                                          target_side, qualification
                                                 ),
                                             FOREIGN KEY (event_id, source_fixture_id)
                                                 REFERENCES murise.fixtures (event_id, id),
                                             FOREIGN KEY (event_id, target_fixture_id)
                                                 REFERENCES murise.fixtures (event_id, id),
                                             CHECK (source_fixture_id <> target_fixture_id)
);

CREATE INDEX ix_fixture_dependencies_target
    ON murise.fixture_dependencies (event_id, target_fixture_id);

CREATE TABLE murise.rounds (
                               id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
                               event_id    uuid NOT NULL REFERENCES murise.events (id),
                               fixture_id  uuid NOT NULL,
                               position    integer NOT NULL CHECK (position > 0),
                               kind        text NOT NULL CHECK (btrim(kind) <> ''),
                               status      text NOT NULL DEFAULT 'scheduled'
                                   CHECK (
                                       status IN ('scheduled', 'active', 'completed', 'voided')
                                       ),
                               deadline_at timestamptz,
                               version     bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
                               created_at  timestamptz NOT NULL DEFAULT now(),
                               updated_at  timestamptz NOT NULL DEFAULT now(),
                               UNIQUE (event_id, id),
                               UNIQUE (fixture_id, position),
                               UNIQUE (event_id, fixture_id, id),
                               FOREIGN KEY (event_id, fixture_id)
                                   REFERENCES murise.fixtures (event_id, id)
);

CREATE TABLE murise.referee_assignments (
                                            id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
                                            event_id      uuid NOT NULL REFERENCES murise.events (id),
                                            membership_id uuid NOT NULL,
                                            event_game_id uuid NOT NULL,
                                            round_id      uuid,
                                            assigned_by   uuid NOT NULL REFERENCES murise.profiles (id),
                                            created_at    timestamptz NOT NULL DEFAULT now(),
                                            revoked_at    timestamptz,
                                            UNIQUE (event_id, id),
                                            FOREIGN KEY (event_id, membership_id)
                                                REFERENCES murise.event_memberships (event_id, id),
                                            FOREIGN KEY (event_id, event_game_id)
                                                REFERENCES murise.event_games (event_id, id),
                                            FOREIGN KEY (event_id, round_id)
                                                REFERENCES murise.rounds (event_id, id),
                                            CHECK (revoked_at IS NULL OR revoked_at >= created_at)
);

CREATE UNIQUE INDEX uq_referee_game_active
    ON murise.referee_assignments (event_id, membership_id, event_game_id)
    WHERE revoked_at IS NULL AND round_id IS NULL;

CREATE UNIQUE INDEX uq_referee_round_active
    ON murise.referee_assignments (event_id, membership_id, round_id)
    WHERE revoked_at IS NULL AND round_id IS NOT NULL;

CREATE INDEX ix_referee_assignments_game
    ON murise.referee_assignments (event_id, event_game_id);

CREATE INDEX ix_referee_assignments_round
    ON murise.referee_assignments (event_id, round_id);

-- Spring Boot also verifies that an assigned round belongs to
-- the specified event_game_id and that REFEREE access is active.

-- ============================================================
-- 9. Result projections
-- ============================================================

CREATE TABLE murise.round_results (
                                      id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
                                      event_id     uuid NOT NULL REFERENCES murise.events (id),
                                      fixture_id   uuid NOT NULL,
                                      round_id     uuid NOT NULL,
                                      team_id      uuid NOT NULL,
                                      raw_value    numeric(12,4) NOT NULL,
                                      outcome      text NOT NULL
                                          CHECK (outcome IN ('win', 'loss', 'draw', 'placed', 'dq')),
                                      evidence     jsonb NOT NULL DEFAULT '{}'::jsonb,
                                      confirmed_by uuid REFERENCES murise.profiles (id),
                                      revision     integer NOT NULL DEFAULT 1 CHECK (revision > 0),
                                      created_at   timestamptz NOT NULL DEFAULT now(),
                                      updated_at   timestamptz NOT NULL DEFAULT now(),
                                      UNIQUE (event_id, id),
                                      UNIQUE (round_id, team_id),
                                      FOREIGN KEY (event_id, fixture_id, round_id)
                                          REFERENCES murise.rounds (event_id, fixture_id, id),
                                      FOREIGN KEY (event_id, fixture_id, team_id)
                                          REFERENCES murise.fixture_teams (event_id, fixture_id, team_id),
                                      CHECK (jsonb_typeof(evidence) = 'object')
);

CREATE INDEX ix_round_results_round
    ON murise.round_results (event_id, fixture_id, round_id);

CREATE INDEX ix_round_results_fixture_team
    ON murise.round_results (event_id, fixture_id, team_id);

CREATE TABLE murise.fixture_results (
                                        id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
                                        event_id   uuid NOT NULL REFERENCES murise.events (id),
                                        fixture_id uuid NOT NULL,
                                        team_id    uuid NOT NULL,
                                        raw_score  numeric(12,4) NOT NULL,
                                        raw_max    numeric(12,4),
                                        outcome    text NOT NULL
                                            CHECK (outcome IN ('win', 'loss', 'draw', 'placed', 'dq')),
                                        evidence   jsonb NOT NULL DEFAULT '{}'::jsonb,
                                        revision   integer NOT NULL DEFAULT 1 CHECK (revision > 0),
                                        created_at timestamptz NOT NULL DEFAULT now(),
                                        updated_at timestamptz NOT NULL DEFAULT now(),
                                        UNIQUE (event_id, id),
                                        UNIQUE (fixture_id, team_id),
                                        FOREIGN KEY (event_id, fixture_id, team_id)
                                            REFERENCES murise.fixture_teams (event_id, fixture_id, team_id),
                                        CHECK (jsonb_typeof(evidence) = 'object')
);

CREATE INDEX ix_fixture_results_fixture_team
    ON murise.fixture_results (event_id, fixture_id, team_id);

CREATE TABLE murise.game_results (
                                     id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
                                     event_id          uuid NOT NULL REFERENCES murise.events (id),
                                     event_game_id     uuid NOT NULL,
                                     team_id           uuid NOT NULL,
                                     raw_score         numeric(12,4) NOT NULL,
                                     raw_max           numeric(12,4),
                                     normalized_points numeric(12,4) NOT NULL,
                                     status            text NOT NULL DEFAULT 'provisional'
                                         CHECK (status IN ('provisional', 'final', 'voided')),
                                     revision          integer NOT NULL DEFAULT 1 CHECK (revision > 0),
                                     created_at        timestamptz NOT NULL DEFAULT now(),
                                     updated_at        timestamptz NOT NULL DEFAULT now(),
                                     UNIQUE (event_id, id),
                                     UNIQUE (event_game_id, team_id),
                                     UNIQUE (event_id, team_id, id),
                                     FOREIGN KEY (event_id, event_game_id)
                                         REFERENCES murise.event_games (event_id, id),
                                     FOREIGN KEY (event_id, team_id)
                                         REFERENCES murise.teams (event_id, id)
);

CREATE INDEX ix_game_results_event_game
    ON murise.game_results (event_id, event_game_id);

-- ============================================================
-- 10. Append-only score ledger
-- ============================================================

CREATE TABLE murise.score_entries (
                                      id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
                                      event_id          uuid NOT NULL REFERENCES murise.events (id),
                                      team_id           uuid NOT NULL,
                                      result_id         uuid,
                                      category          text NOT NULL CHECK (
                                          category IN ('game_points', 'bonus', 'penalty', 'adjustment', 'reversal')
                                          ),
                                      delta             numeric(12,4) NOT NULL,
                                      source_key        text NOT NULL CHECK (btrim(source_key) <> ''),
                                      reason            text NOT NULL CHECK (btrim(reason) <> ''),
                                      actor_id          uuid NOT NULL REFERENCES murise.profiles (id),
                                      reverses_entry_id uuid,
                                      created_at        timestamptz NOT NULL DEFAULT now(),
                                      UNIQUE (event_id, id),
                                      UNIQUE (event_id, source_key),
                                      UNIQUE (event_id, team_id, id),
                                      UNIQUE (reverses_entry_id),
                                      FOREIGN KEY (event_id, team_id)
                                          REFERENCES murise.teams (event_id, id),
                                      FOREIGN KEY (event_id, team_id, result_id)
                                          REFERENCES murise.game_results (event_id, team_id, id),
                                      FOREIGN KEY (event_id, team_id, reverses_entry_id)
                                          REFERENCES murise.score_entries (event_id, team_id, id),
                                      CHECK (
                                          (category = 'reversal' AND reverses_entry_id IS NOT NULL)
                                              OR
                                          (category <> 'reversal' AND reverses_entry_id IS NULL)
                                          ),
                                      CHECK (reverses_entry_id IS NULL OR reverses_entry_id <> id)
);

CREATE INDEX ix_score_entries_result
    ON murise.score_entries (event_id, team_id, result_id);

-- guess_id and its composite FK are added with Scene mechanics.

-- ============================================================
-- 11. Live event state
-- ============================================================

CREATE TABLE murise.event_state (
                                    event_id              uuid PRIMARY KEY REFERENCES murise.events (id),
                                    current_event_game_id uuid,
                                    phase                 text NOT NULL CHECK (btrim(phase) <> ''),
                                    deadline_at           timestamptz,
                                    paused_remaining_ms   bigint CHECK (paused_remaining_ms >= 0),
                                    version               bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
                                    updated_at            timestamptz NOT NULL DEFAULT now(),
                                    FOREIGN KEY (event_id, current_event_game_id)
                                        REFERENCES murise.event_games (event_id, id)
);

-- ============================================================
-- 12. Frozen scoreboard snapshots
-- ============================================================

CREATE TABLE murise.scoreboard_snapshots (
                                             id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
                                             event_id   uuid NOT NULL REFERENCES murise.events (id),
                                             created_by uuid NOT NULL REFERENCES murise.profiles (id),
                                             created_at timestamptz NOT NULL DEFAULT now(),
                                             UNIQUE (event_id, id)
);

CREATE TABLE murise.scoreboard_snapshot_rows (
                                                 event_id    uuid NOT NULL,
                                                 snapshot_id uuid NOT NULL,
                                                 team_id     uuid NOT NULL,
                                                 points      numeric(12,4) NOT NULL,
                                                 rank        integer NOT NULL CHECK (rank > 0),
                                                 PRIMARY KEY (snapshot_id, team_id),
                                                 FOREIGN KEY (event_id, snapshot_id)
                                                     REFERENCES murise.scoreboard_snapshots (event_id, id),
                                                 FOREIGN KEY (event_id, team_id)
                                                     REFERENCES murise.teams (event_id, id)
);

CREATE INDEX ix_snapshot_rows_team
    ON murise.scoreboard_snapshot_rows (event_id, team_id);

ALTER TABLE murise.events
    ADD COLUMN frozen_snapshot_id uuid,
    ADD CONSTRAINT fk_events_frozen_snapshot
        FOREIGN KEY (id, frozen_snapshot_id)
            REFERENCES murise.scoreboard_snapshots (event_id, id),
    ADD CONSTRAINT ck_events_frozen_snapshot
        CHECK (
            scoreboard_visibility <> 'frozen'
                OR frozen_snapshot_id IS NOT NULL
            );

-- ============================================================
-- 13. Command receipts and audit trail
-- ============================================================

CREATE TABLE murise.command_receipts (
                                         id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
                                         event_id        uuid NOT NULL REFERENCES murise.events (id),
                                         actor_id        uuid NOT NULL REFERENCES murise.profiles (id),
                                         command_type    text NOT NULL CHECK (btrim(command_type) <> ''),
                                         idempotency_key text NOT NULL CHECK (btrim(idempotency_key) <> ''),
                                         request_hash    text NOT NULL CHECK (btrim(request_hash) <> ''),
                                         response        jsonb NOT NULL,
                                         created_at      timestamptz NOT NULL DEFAULT now(),
                                         UNIQUE (event_id, id),
                                         UNIQUE (event_id, actor_id, command_type, idempotency_key)
);

CREATE TABLE murise.audit_events (
                                     id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
                                     event_id    uuid REFERENCES murise.events (id),
                                     actor_id    uuid REFERENCES murise.profiles (id),
                                     action      text NOT NULL CHECK (btrim(action) <> ''),
                                     entity_type text NOT NULL CHECK (btrim(entity_type) <> ''),
                                     entity_id   uuid,
                                     details     jsonb NOT NULL DEFAULT '{}'::jsonb,
                                     created_at  timestamptz NOT NULL DEFAULT now(),
                                     UNIQUE (event_id, id),
                                     CHECK (jsonb_typeof(details) = 'object')
);

CREATE INDEX ix_audit_events_time
    ON murise.audit_events (event_id, created_at DESC);

CREATE INDEX ix_audit_events_actor
    ON murise.audit_events (actor_id, created_at DESC);

-- ============================================================
-- 14. Media metadata
-- ============================================================

CREATE TABLE murise.media_assets (
                                     id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
                                     event_id    uuid NOT NULL REFERENCES murise.events (id),
                                     owner_id    uuid NOT NULL REFERENCES murise.profiles (id),
                                     bucket      text NOT NULL CHECK (btrim(bucket) <> ''),
                                     object_path text NOT NULL CHECK (btrim(object_path) <> ''),
                                     mime_type   text NOT NULL CHECK (btrim(mime_type) <> ''),
                                     byte_size   bigint NOT NULL CHECK (byte_size >= 0),
                                     status      text NOT NULL DEFAULT 'pending'
                                         CHECK (status IN ('pending', 'ready', 'failed', 'deleted')),
                                     created_at  timestamptz NOT NULL DEFAULT now(),
                                     updated_at  timestamptz NOT NULL DEFAULT now(),
                                     UNIQUE (event_id, id),
                                     UNIQUE (bucket, object_path)
);

CREATE INDEX ix_media_assets_owner
    ON murise.media_assets (owner_id);

-- ============================================================
-- 15. Automatic updated_at timestamps
-- ============================================================

CREATE FUNCTION murise.touch_updated_at()
    RETURNS trigger
    LANGUAGE plpgsql
    SET search_path = pg_catalog
AS $$
BEGIN
    NEW.updated_at := statement_timestamp();
    RETURN NEW;
END;
$$;

DO $$
    DECLARE
        table_name text;
    BEGIN
        FOREACH table_name IN ARRAY ARRAY[
            'profiles',
            'game_definitions',
            'game_versions',
            'events',
            'event_memberships',
            'join_requests',
            'teams',
            'event_participants',
            'event_games',
            'schedule_versions',
            'schedule_slots',
            'event_resources',
            'fixtures',
            'rounds',
            'round_results',
            'fixture_results',
            'game_results',
            'event_state',
            'media_assets'
            ]
            LOOP
                EXECUTE format(
                        'CREATE TRIGGER set_updated_at
                         BEFORE UPDATE ON murise.%I
                         FOR EACH ROW EXECUTE FUNCTION murise.touch_updated_at()',
                        table_name
                        );
            END LOOP;
    END;
$$;

-- ============================================================
-- 16. Catalog immutability
-- ============================================================

CREATE FUNCTION murise.protect_game_definition_code()
    RETURNS trigger
    LANGUAGE plpgsql
    SET search_path = pg_catalog
AS $$
BEGIN
    IF NEW.code IS DISTINCT FROM OLD.code THEN
        RAISE EXCEPTION 'Game definition codes are immutable';
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER protect_game_definition_code
    BEFORE UPDATE ON murise.game_definitions
    FOR EACH ROW
EXECUTE FUNCTION murise.protect_game_definition_code();

CREATE FUNCTION murise.protect_game_version()
    RETURNS trigger
    LANGUAGE plpgsql
    SET search_path = pg_catalog
AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        IF OLD.status IN ('published', 'archived') THEN
            RAISE EXCEPTION
                'Published or archived game versions cannot be deleted';
        END IF;

        -- Referenced drafts are also protected by foreign keys.
        RETURN OLD;
    END IF;

    IF OLD.status IN ('published', 'archived') THEN
        IF (to_jsonb(NEW) - 'status' - 'updated_at')
            IS DISTINCT FROM
            (to_jsonb(OLD) - 'status' - 'updated_at')
        THEN
            RAISE EXCEPTION
                'Published game content is immutable; create a new version';
        END IF;

        IF OLD.status = 'published'
            AND NEW.status NOT IN ('published', 'archived')
        THEN
            RAISE EXCEPTION 'Published versions cannot return to draft';
        END IF;

        IF OLD.status = 'archived' AND NEW.status <> 'archived' THEN
            RAISE EXCEPTION 'Archived versions cannot be reactivated';
        END IF;
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER protect_game_version
    BEFORE UPDATE OR DELETE ON murise.game_versions
    FOR EACH ROW
EXECUTE FUNCTION murise.protect_game_version();

-- ============================================================
-- 17. Ledger reversal validation
-- ============================================================

CREATE FUNCTION murise.validate_score_reversal()
    RETURNS trigger
    LANGUAGE plpgsql
    SET search_path = pg_catalog
AS $$
DECLARE
    original murise.score_entries%ROWTYPE;
BEGIN
    IF NEW.category <> 'reversal' THEN
        RETURN NEW;
    END IF;

    SELECT *
    INTO original
    FROM murise.score_entries
    WHERE id = NEW.reverses_entry_id
      AND event_id = NEW.event_id
      AND team_id = NEW.team_id;

    IF NOT FOUND THEN
        RAISE EXCEPTION
            'Reversal must reference an existing entry for the same event/team';
    END IF;

    IF original.category = 'reversal' THEN
        RAISE EXCEPTION 'A reversal cannot reverse another reversal';
    END IF;

    IF NEW.delta IS DISTINCT FROM -original.delta THEN
        RAISE EXCEPTION 'Reversal delta must negate the full original delta';
    END IF;

    -- Preserve result attribution for visibility and reporting.
    IF NEW.result_id IS DISTINCT FROM original.result_id THEN
        RAISE EXCEPTION 'Reversal must preserve the original result reference';
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER validate_score_reversal
    BEFORE INSERT ON murise.score_entries
    FOR EACH ROW
EXECUTE FUNCTION murise.validate_score_reversal();

-- Original entries cannot change. UNIQUE(reverses_entry_id)
-- prevents concurrent requests from reversing one entry twice.
-- The backend inserts reversal + replacement in one transaction.

-- ============================================================
-- 18. Append-only protection
-- ============================================================

CREATE FUNCTION murise.reject_history_mutation()
    RETURNS trigger
    LANGUAGE plpgsql
    SET search_path = pg_catalog
AS $$
BEGIN
    RAISE EXCEPTION '% is append-only; % is forbidden',
        TG_TABLE_NAME, TG_OP;
END;
$$;

CREATE TRIGGER protect_score_entries
    BEFORE UPDATE OR DELETE OR TRUNCATE ON murise.score_entries
    FOR EACH STATEMENT
EXECUTE FUNCTION murise.reject_history_mutation();

CREATE TRIGGER protect_audit_events
    BEFORE UPDATE OR DELETE OR TRUNCATE ON murise.audit_events
    FOR EACH STATEMENT
EXECUTE FUNCTION murise.reject_history_mutation();

-- ============================================================
-- 19. Explicit runtime privileges
-- ============================================================

-- No blanket GRANT ON ALL TABLES:
-- Flyway history must remain inaccessible to the runtime role.
--
-- Mutable entities receive SELECT/INSERT/UPDATE only.
-- Deactivation, revocation and archival use their status fields.

DO $$
    DECLARE
        table_name text;
    BEGIN
        FOREACH table_name IN ARRAY ARRAY[
            'platform_role_grants',
            'game_definitions',
            'game_versions',
            'events',
            'event_memberships',
            'event_role_grants',
            'event_invites',
            'join_requests',
            'teams',
            'event_participants',
            'event_games',
            'schedule_versions',
            'schedule_slots',
            'event_resources',
            'fixtures',
            'rounds',
            'referee_assignments',
            'round_results',
            'fixture_results',
            'game_results',
            'event_state',
            'media_assets'
            ]
            LOOP
                EXECUTE format(
                        'REVOKE ALL ON TABLE murise.%I
                         FROM PUBLIC, anon, authenticated, murise_app',
                        table_name
                        );

                EXECUTE format(
                        'GRANT SELECT, INSERT, UPDATE ON TABLE murise.%I TO murise_app',
                        table_name
                        );
            END LOOP;

        -- Bridges may be edited while their schedule/fixture is editable.
        -- Spring Boot prevents changes to historical participation.
        FOREACH table_name IN ARRAY ARRAY[
            'slot_resource_assignments',
            'fixture_teams',
            'fixture_dependencies'
            ]
            LOOP
                EXECUTE format(
                        'REVOKE ALL ON TABLE murise.%I
                         FROM PUBLIC, anon, authenticated, murise_app',
                        table_name
                        );

                EXECUTE format(
                        'GRANT SELECT, INSERT, UPDATE, DELETE
                         ON TABLE murise.%I TO murise_app',
                        table_name
                        );
            END LOOP;

        -- History, snapshots and completed command receipts:
        -- runtime may read and append, but never rewrite or truncate.
        FOREACH table_name IN ARRAY ARRAY[
            'score_entries',
            'scoreboard_snapshots',
            'scoreboard_snapshot_rows',
            'command_receipts',
            'audit_events'
            ]
            LOOP
                EXECUTE format(
                        'REVOKE ALL ON TABLE murise.%I
                         FROM PUBLIC, anon, authenticated, murise_app',
                        table_name
                        );

                EXECUTE format(
                        'GRANT SELECT, INSERT ON TABLE murise.%I TO murise_app',
                        table_name
                        );
            END LOOP;
    END;
$$;

REVOKE ALL ON FUNCTION murise.touch_updated_at()
    FROM PUBLIC, anon, authenticated;

REVOKE ALL ON FUNCTION murise.protect_game_definition_code()
    FROM PUBLIC, anon, authenticated;

REVOKE ALL ON FUNCTION murise.protect_game_version()
    FROM PUBLIC, anon, authenticated;

REVOKE ALL ON FUNCTION murise.validate_score_reversal()
    FROM PUBLIC, anon, authenticated;

REVOKE ALL ON FUNCTION murise.reject_history_mutation()
    FROM PUBLIC, anon, authenticated;

-- Future migrations must explicitly grant runtime table access.
-- Defaults apply to objects created by murise_migrator.

ALTER DEFAULT PRIVILEGES FOR ROLE murise_migrator IN SCHEMA murise
    REVOKE ALL ON TABLES FROM PUBLIC, anon, authenticated, murise_app;

ALTER DEFAULT PRIVILEGES FOR ROLE murise_migrator IN SCHEMA murise
    REVOKE ALL ON SEQUENCES FROM PUBLIC, anon, authenticated, murise_app;

-- Function EXECUTE is granted to PUBLIC by PostgreSQL by default.
-- Removing that default requires the role-wide form.
ALTER DEFAULT PRIVILEGES FOR ROLE murise_migrator
    REVOKE EXECUTE ON FUNCTIONS FROM PUBLIC;

ALTER DEFAULT PRIVILEGES FOR ROLE murise_migrator IN SCHEMA murise
    REVOKE EXECUTE ON FUNCTIONS FROM anon, authenticated, murise_app;