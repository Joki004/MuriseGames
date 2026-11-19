# For The Plot — Database Architecture Specification

**Status:** Architecture Agreed 
**Stack:** PostgreSQL (Supabase) · Flyway Migrations · Spring Boot Authoritative API · Next.js Client  
**Schema Isolation:** Non-exposed `domain` schema owned by Flyway; `auth` and `storage` remain Supabase-managed.

---

## 1. Core Architectural Principles

1. **Single Catalog + Pinned Versions:** One `game_definitions` table holds the 9 core activities. Events never duplicate game tables (no `CupEvents` or `PoisonEvents`); instead, `event_games` binds an event to an immutable `game_version_id`.
2. **Event-Scoped Composite Integrity:** Every event-owned parent table exposes `UNIQUE(event_id, id)`. Child tables reference parents via composite foreign keys `(event_id, parent_id)` so cross-event references are impossible at the database level.
3. **Auth vs. Roster Separation:** `event_memberships` and `event_role_grants` authorize user accounts, while `event_participants` is the single source of truth for team assignment (`team_id`), guest players (`user_id IS NULL`), and captaincy.
4. **Three-Tier Score Rollup + Append-Only Ledger:** Raw play rolls up from `round_results` $\rightarrow$ `fixture_results` $\rightarrow$ `game_results`, which settle into an immutable `score_entries` ledger. Corrections are recorded via reversal entries (`reverses_entry_id`), never via `UPDATE` or `DELETE`.
5. **Exact Numeric Precision:** All raw scores, point deltas, and normalized totals use fixed-point `numeric` types (e.g., `numeric(12,4)`), never binary floating-point types (`real`/`double precision`).
6. **Configurable Scoreboard Visibility:** Organizers can hide or reveal live standings at the event level (`events.scoreboard_visibility`) with per-game overrides (`event_games.scoreboard_visibility_override`). Underlying ledger calculations run normally while Spring Boot strips hidden totals from player-facing DTOs.

---

## 2. Schema Reference by Layer

### Layer 1: Global Catalog & Versioning

#### `domain.game_definitions`
Master catalog of the 9 core activities (Scene, Alibi, Social, Cup, Poison, Grid, Sensory, Group Chat, Rehearsal).
- `id` (`uuid` PK)
- `code` (`text` UNIQUE NOT NULL) — e.g., `'scene'`, `'alibi'`, `'cup'`
- `name` (`text` NOT NULL)
- `kind` (`text` NOT NULL CHECK) — `'competitive' | 'party' | 'social_deduction' | 'rehearsal'`
- `active` (`boolean` NOT NULL DEFAULT `true`)
- `created_at`, `updated_at` (`timestamptz`)

#### `domain.game_versions`
Immutable, version-pinned rules, scoring configuration, and JSON validation schemas for a game definition.
- `id` (`uuid` PK)
- `game_definition_id` (`uuid` FK $\rightarrow$ `game_definitions.id`)
- `version` (`integer` NOT NULL CHECK `> 0`)
- `summary` (`text` NOT NULL) — Short lobby/card description of the game
- `rules_markdown` (`text` NOT NULL) — Full step-by-step gameplay rules and tie-breakers
- `end_game_scoring_summary` (`text` NOT NULL) — Human-readable explanation of how outcomes convert to event points
- `participation_mode` (`text` NOT NULL CHECK) — `'all_teams' | 'head_to_head' | 'multi_team_pod' | 'individual'`
- `scoring_handler` (`text` NOT NULL) — Identifier for the Spring Boot scoring strategy bean
- `scoring_config` (`jsonb` NOT NULL) — Deterministic parameters for `scoring_handler` (e.g., rank point tables)
- `settings_schema` (`jsonb` NOT NULL) — JSON Schema validating `event_games.settings_snapshot`
- `result_schema` (`jsonb` NOT NULL) — JSON Schema validating `round_results.evidence` and `fixture_results.evidence`
- `status` (`text` NOT NULL CHECK) — `'draft' | 'published' | 'archived'`
- `source_ref` (`text` NULL) — Rulebook slide/section reference
- `created_at`, `updated_at` (`timestamptz`)
- **Constraints:** `UNIQUE(game_definition_id, version)`, `UNIQUE(game_definition_id, id)` (composite target for version pinning), and a `BEFORE UPDATE/DELETE` trigger blocking edits once `status IN ('published', 'archived')` or referenced by `event_games`.

---

### Layer 2: Identity, Events, Access & Roster

#### `domain.profiles` & `domain.platform_role_grants`
- `profiles(id PK -> auth.users.id, display_name, created_at, updated_at)`
- `platform_role_grants(user_id FK -> profiles.id, role, created_at)` — Global admin/staff privileges (`'platform_admin' | 'catalog_editor'`).

#### `domain.events`
Central anchor for a game night.
- `id` (`uuid` PK)
- `name` (`text` NOT NULL)
- `theme` (`text` NULL)
- `starts_at` (`timestamptz` NOT NULL)
- `timezone` (`text` NOT NULL)
- `venue` (`text` NULL)
- `status` (`text` NOT NULL CHECK) — `'draft' | 'published' | 'live' | 'completed' | 'archived'`
- `scoreboard_visibility` (`text` NOT NULL CHECK DEFAULT `'public'`) — `'public' | 'hidden' | 'frozen'`
- `created_by` (`uuid` FK $\rightarrow$ `auth.users.id`)
- `created_at`, `updated_at` (`timestamptz`)

#### `domain.event_memberships` & `domain.event_role_grants`
Controls account access and permissions within an event (kept separate from team roster).
- `event_memberships(id PK, event_id FK, user_id FK, status, created_at, updated_at)` — `UNIQUE(event_id, user_id)`
- `event_role_grants(event_id, membership_id, role, created_at)` — Composite FK `(event_id, membership_id)` $\rightarrow$ `event_memberships(event_id, id)`.
- **Supported Event Roles (`CHECK`):**
  - `'organizer'` — Full event configuration, roster/team management, schedule publishing, scoreboard visibility control, and ledger adjustments/reversals.
  - `'host'` — Live event pacing (`event_state` phase/timer control), clue releases, and full visibility into standings.
  - `'referee'` — Table/match officiating: records and confirms `round_results` and `fixture_results`, reviews Scene guesses (`guess_reviews`), and validates rule compliance at stations/tables without full event-admin privileges.
  - `'player'` — Standard competitor: views public rules/schedules/standings and submits team gameplay actions.
  - `'spectator'` — Read-only access to public lobby, schedule, and visible standings.

#### `domain.event_invites` & `domain.join_requests`
- `event_invites(id PK, event_id FK, token_hash, expires_at, revoked_at, max_uses, use_count, created_at)`
- `join_requests(id PK, event_id FK, user_id FK, status, decided_by FK, created_at, updated_at)`

#### `domain.teams`
- `id` (`uuid` PK)
- `event_id` (`uuid` FK $\rightarrow$ `events.id`)
- `name` (`text` NOT NULL)
- `color` (`text` NOT NULL)
- `sort_order` (`integer` NOT NULL DEFAULT `0`)
- `active` (`boolean` NOT NULL DEFAULT `true`)
- `created_at`, `updated_at` (`timestamptz`)
- **Constraints:** `UNIQUE(event_id, id)`, `UNIQUE(event_id, name)`.

#### `domain.event_participants`
Single source of truth for team assignment and captaincy.
- `id` (`uuid` PK)
- `event_id` (`uuid` FK $\rightarrow$ `events.id`)
- `user_id` (`uuid` NULL FK $\rightarrow$ `auth.users.id`) — Nullable to allow accountless guest entries until claimed with organizer verification
- `display_name` (`text` NOT NULL)
- `team_id` (`uuid` NULL) — Composite FK `(event_id, team_id)` $\rightarrow$ `teams(event_id, id)`
- `status` (`text` NOT NULL CHECK) — `'invited' | 'active' | 'benched' | 'removed'`
- `is_captain` (`boolean` NOT NULL DEFAULT `false`)
- `created_at`, `updated_at` (`timestamptz`)
- **Constraints:**
  - `CHECK (NOT is_captain OR team_id IS NOT NULL)` — A captain must belong to a team.
  - Partial unique index `ON (event_id, team_id) WHERE is_captain = true AND status = 'active'` — Enforces at most one active captain per team (grants elevated team-submission privileges in Spring Boot without creating a separate `event_role_grants` role).

---

### Layer 3: Event Lineup, Schedule & Fixtures

#### `domain.event_games`
Binds an event to a selected game definition and pinned version.
- `id` (`uuid` PK)
- `event_id` (`uuid` FK $\rightarrow$ `events.id`)
- `game_definition_id` (`uuid` NOT NULL)
- `game_version_id` (`uuid` NOT NULL)
- `settings_snapshot` (`jsonb` NOT NULL) — Validated against `game_versions.settings_schema`
- `scoreboard_visibility_override` (`text` NULL CHECK) — `'public' | 'hidden' | NULL` (inherits `events.scoreboard_visibility` when `NULL`)
- `house_rules_note` (`text` NULL) — Optional organizer note appended to the rulebook view
- `status` (`text` NOT NULL CHECK) — `'scheduled' | 'active' | 'settled' | 'voided'`
- `sort_order` (`integer` NOT NULL)
- `created_at`, `updated_at` (`timestamptz`)
- **Constraints:** `UNIQUE(event_id, id)`, `UNIQUE(event_id, game_definition_id)` (V1 enforces at most one instance per game definition per event), and composite FK `(game_definition_id, game_version_id)` $\rightarrow$ `game_versions(game_definition_id, id)`.

#### `domain.schedule_versions`, `domain.schedule_slots`, `domain.event_resources`, `domain.slot_resource_assignments`
- `schedule_versions(id PK, event_id FK, mode, algorithm_version, seed, config, status, version, created_at, updated_at)` — `mode` (`'balanced' | 'round_robin' | 'manual'`). When publishing in `'balanced'` mode, Spring Boot locks the event row and validates every active team appears in exactly 1 fixture per `event_game`.
- `schedule_slots(id PK, event_id, schedule_version_id, event_game_id, start_at, end_at, created_at, updated_at)` — Composite FKs to `schedule_versions` and `event_games`.
- `event_resources(id PK, event_id FK, type, name, capacity, created_at, updated_at)` — Physical tables, rooms, or stations.
- `slot_resource_assignments(slot_id, resource_id)` — Composite PK linking slots to resources.

#### `domain.fixtures`, `domain.fixture_teams`, `domain.fixture_dependencies`, `domain.rounds`
- `fixtures(id PK, event_id, event_game_id, schedule_version_id, slot_id, round_number, status, created_at, updated_at)` — `UNIQUE(event_id, id)`.
- `fixture_teams(event_id, fixture_id, team_id, side, created_at)` — `PRIMARY KEY (fixture_id, team_id)`, `UNIQUE(event_id, fixture_id, team_id)`. Authoritative bridge defining which teams participate in a fixture.
- `fixture_dependencies(event_id, source_fixture_id, target_fixture_id, target_side, qualification)` — Models bracket/elimination progression between fixtures.
- `rounds(id PK, event_id, fixture_id, position, kind, status, deadline_at, version, created_at, updated_at)` — Sub-rounds or turns inside a fixture.

---

### Layer 4: Results & Immutable Points Ledger

#### `domain.round_results`
- `id` (`uuid` PK), `event_id`, `round_id`, `team_id`, `raw_value` (`numeric(12,4)`), `outcome`, `evidence` (`jsonb`), `confirmed_by` (`uuid` NULL — references referee/host/organizer `auth.users.id`), `revision` (`integer`), `created_at`, `updated_at`.
- **Constraints:** `UNIQUE(round_id, team_id)` and composite FK ensuring `team_id` is registered in the parent fixture's `fixture_teams`.

#### `domain.fixture_results`
- `id` (`uuid` PK), `event_id`, `fixture_id`, `team_id`, `raw_score` (`numeric(12,4)`), `raw_max` (`numeric(12,4)` NULL), `outcome` (`'win' | 'loss' | 'draw' | 'placed' | 'dq'`), `evidence` (`jsonb`), `revision` (`integer`), `created_at`, `updated_at`.
- **Constraints:** `UNIQUE(fixture_id, team_id)` and composite FK `(event_id, fixture_id, team_id)` $\rightarrow$ `fixture_teams(event_id, fixture_id, team_id)`. Non-participating teams cannot receive fixture results.

#### `domain.game_results`
- `id` (`uuid` PK), `event_id`, `event_game_id`, `team_id`, `raw_score` (`numeric(12,4)`), `raw_max` (`numeric(12,4)` NULL), `normalized_points` (`numeric(12,4)`), `status` (`'provisional' | 'final' | 'voided'`), `revision` (`integer`), `created_at`, `updated_at`.
- **Constraints:** `UNIQUE(event_game_id, team_id)` and composite FKs to `event_games` and `teams`.

#### `domain.score_entries`
Append-only ledger driving the event leaderboard.
- `id` (`uuid` PK)
- `event_id` (`uuid` FK $\rightarrow$ `events.id`)
- `team_id` (`uuid` NOT NULL) — Composite FK `(event_id, team_id)` $\rightarrow$ `teams(event_id, id)`
- `result_id` (`uuid` NULL FK $\rightarrow$ `game_results.id`)
- `guess_id` (`uuid` NULL FK $\rightarrow$ `guesses.id`) — Used for mid-game Scene guess bonuses/penalties
- `category` (`text` NOT NULL CHECK) — `'game_points' | 'bonus' | 'penalty' | 'adjustment' | 'reversal'`
- `delta` (`numeric(12,4)` NOT NULL)
- `source_key` (`text` NOT NULL) — Deterministic idempotency key (e.g., `game_result:{id}:rev:{rev}`)
- `reason` (`text` NOT NULL)
- `actor_id` (`uuid` NOT NULL FK $\rightarrow$ `auth.users.id`)
- `reverses_entry_id` (`uuid` NULL FK $\rightarrow$ `score_entries.id`)
- `created_at` (`timestamptz` NOT NULL)
- **Constraints:** `UNIQUE(event_id, source_key)`. `UPDATE` and `DELETE` are revoked from the runtime database role.

---

### Layer 5: Dedicated Mechanics vs. Shared-Only Games

| Game | Persistence Model | Tables Used |
| :--- | :--- | :--- |
| **Cup, Poison, Grid, Sensory** | **Shared Only** | `fixtures` $\rightarrow$ `rounds` $\rightarrow$ `round_results` (strict `result_schema` JSONB) $\rightarrow$ `fixture_results` $\rightarrow$ `game_results`. |
| **Rehearsal** | **Schedule / State Only** | `schedule_slots` and `event_state`. No scoring ledger entries. |
| **Group Chat** | **Shared Results (+ Optional Content)** | Shared results pipeline; uses generic `event_content_assignments` + `media_assets` if the private-slide variant is confirmed in GM-005. |
| **Scene** | **Dedicated (7 Tables)** | `scene_entries`, `scene_aliases`, `scene_clues`, `guessing_state`, `clue_releases`, `guesses`, `guess_reviews` — handles progressive clue releases, alias matching, and referee/host moderation queues. |
| **Alibi** | **Dedicated (7 Tables)** | `alibi_case_versions`, `alibi_questions`, `alibi_rounds`, `alibi_assignments`, `alibi_prompts`, `alibi_answers`, `alibi_verdicts` — handles asymmetric suspect/interrogator prompt distribution and verdict scoring. |
| **Social** | **Dedicated (5 Tables)** | `social_case_versions`, `social_rules`, `social_matches`, `social_turns`, `social_observations` — handles hidden-rule social deduction turns and observation logs. |

---

### Layer 6: Live Operations, Idempotency & Media

- `domain.event_state(event_id PK/FK, current_event_game_id FK, phase, deadline_at, paused_remaining_ms, version, updated_at)` — Single row per event tracking live phase transitions and timer state, protected by optimistic locking (`version`).
- `domain.command_receipts(id PK, event_id FK, actor_id, command_type, idempotency_key, request_hash, response, created_at)` — `UNIQUE(event_id, command_type, idempotency_key)`. Ensures retries of state-mutating Spring Boot commands return cached responses safely.
- `domain.audit_events(id PK, event_id FK, actor_id, action, entity_type, entity_id, details, created_at)` — Append-only operational audit trail.
- `domain.media_assets(id PK, event_id FK, owner_id, bucket, object_path, mime_type, byte_size, status, created_at, updated_at)` — Tracks metadata for files stored in Supabase Storage.

---

## 3. Security, Migrations & Concurrency Contract

1. **Migration Ownership:** Flyway inside the Spring Boot service owns all DDL via a dedicated `flyway_migrator` database role.
2. **PostgREST Lockdown:** `REVOKE ALL ON SCHEMA domain FROM anon, authenticated, public;`. The browser never reads or writes `domain` tables directly via Supabase PostgREST.
3. **Runtime JDBC Privileges:** Spring Boot connects using a restricted `spring_api_runtime` role granted `SELECT, INSERT, UPDATE` on mutable tables and `SELECT, INSERT` only on append-only tables (`score_entries`, `audit_events`, `command_receipts`). `DELETE` and `TRUNCATE` are revoked across transactional tables; records are archived or voided via status transitions.
4. **Secret Projections:** Spring Boot verifies Supabase JWTs and live `event_memberships`/`event_role_grants` on every request. Player-facing DTOs omit unreleased `scene_clues`, secret `alibi_questions`, hidden `social_rules`, and hidden scoreboard totals when `scoreboard_visibility = 'hidden'`.
5. **Transactional Locking:** All settlement, schedule publication, and phase-advance endpoints run inside `@Transactional` boundaries using `SELECT ... FOR UPDATE` on `event_state` or `events` alongside `command_receipts` idempotency checks.