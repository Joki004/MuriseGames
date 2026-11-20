# MuriseGames — Database Architecture

**Status:** Proposed implementation specification  
**Stack:** Next.js · Spring Boot · Supabase PostgreSQL/Auth/Storage · Flyway  
**Application schema:** `murise`  
**Schema owner:** `postgres`  
**Migration role:** `murise_migrator`  
**Runtime database role:** `murise_app`

This document describes the target architecture. Tables and constraints exist
only after their corresponding Flyway migrations have been implemented.

Supabase manages the `auth` and `storage` schemas.

---

## 1. Core Principles

1. One shared game catalog, with immutable published game versions.
2. Shared event, team, fixture and result tables; dedicated mechanics tables
   only where a game needs private or interactive data.
3. Authentication, event permissions and team participation are separate.
4. Spring Boot authorizes application operations and owns business transactions.
5. Events reference pinned game versions and validated settings.
6. Event-owned relationships use composite foreign keys to prevent
   cross-event references.
7. Scores use exact `numeric` values, never floating-point types.
8. Awarded points are recorded in an append-only ledger.
9. Corrections preserve history through reversals and replacement entries.
10. Hidden information is excluded from unauthorized API responses.

### Database conventions

- Entity IDs are UUIDs unless otherwise stated.
- Timestamps use `timestamptz`.
- Scores use `numeric(12,4)`.
- Entity tables normally include `created_at` and `updated_at`.
- Append-only tables include `created_at`.
- Every event-owned entity with an `id` exposes `UNIQUE(event_id, id)`.
- Event-owned child references include `event_id`.
- Referenced composite column sets must have matching unique constraints.
- Nullable fields are explicitly identified below.
- Mutable entities use statuses instead of destructive deletion.
- Enumerated statuses and roles must have database CHECK constraints.

These conventions must be implemented in migrations; they are not automatic.

---

## 2. User Roles and Event Permissions

### 2.1 Platform scope

| Role | Meaning |
|---|---|
| USER | Every authenticated account; may create events and join authorized events. |
| ADMIN | Explicit global administrative grant for platform management and audited intervention. |

`USER` is the default capability of an authenticated account. It does not
require a stored role-grant row.

`ADMIN` is stored in `platform_role_grants`.

An ADMIN remains an ordinary account and can also have event memberships.

Platform ADMIN is not the PostgreSQL `postgres` role, the `murise_app`
database login, or a Supabase service key.

Signup metadata must never grant ADMIN.

### 2.2 Event scope

| Role | Permissions within the assigned event |
|---|---|
| ORGANIZER | Manage event configuration, teams, memberships, role delegation, schedule, visibility and score corrections. |
| REFEREE | Officiate assigned games or rounds and record/review permitted results. |
| PLAYER | Participate through the account's assigned participant/team record. |
| VIEWER | Read authorized event information and released standings. |

Optional future role:

| Role | Purpose |
|---|---|
| HOST | Run timers, phases and releases without full organizer administration. |

For V1, organizers perform host actions. A separate HOST role is not required.

Terminology:
- “Participant” describes a roster entry.
- `PLAYER` is an authorization role.
- “Judge” means `REFEREE`.
- “Spectator” means `VIEWER`.

Use these canonical role names consistently in SQL, Java and API contracts.

### 2.3 How roles are stored

- `profiles`: the account's application profile.
- `platform_role_grants`: explicit global ADMIN grants.
- `event_memberships`: connects an account to an event.
- `event_role_grants`: assigns roles to that membership.
- `event_participants`: attendance, team assignment and captaincy.
- `referee_assignments`: limits referee authority to assigned activities.

Do not add `organizer_id`, `referee_id` or participant arrays to `events`.

`events.created_by` records who created an event. It is not the source of
current authorization.

Example:

| Account | Platform | Event A | Event B |
|---|---|---|---|
| Alice | USER | ORGANIZER | PLAYER |
| Bob | USER | PLAYER | REFEREE |
| Carol | ADMIN | No ordinary membership | VIEWER |

A membership may hold multiple roles, such as ORGANIZER and PLAYER.
Organizer access includes event secrets, so organizer participation cannot
guarantee the same information boundaries as ordinary player participation.

### 2.4 Authorization rules

- A verified login alone grants no private event access.
- Normal event access requires an active membership and the required active role.
- Team actions additionally require an active linked participant.
- Captain-only actions require that participant to be the active team captain.
- Referees require both the REFEREE role and a matching active assignment.
- Role and membership revocation take effect on the next API request.
- Event roles are read from the database, not trusted from signup metadata.
- Creating an event creates its first organizer membership/grant atomically.
- Removing or deactivating the final active organizer is forbidden.
- All paths that can remove organizer access lock the same event row before
  checking the remaining organizers.
- Global ADMIN intervention is explicit, authorized and audited.
- Global administrative access does not silently make an account an organizer.

---

## 3. Global Catalog and Versioning

### `murise.game_definitions`

One row per catalog activity.

Fields:
- `id`
- `code`: unique, immutable application identifier
- `name`
- `kind`: competitive, party, social_deduction, rehearsal
- `active`: boolean, default true
- `created_at`, `updated_at`

Initial catalog:
- Grab the Cup
- The Sensory Chain
- Unified Alibi
- Social Scenario
- Poison Game
- Rhythm Grid Hunt
- Group Chat Meltdown
- Final Rehearsal
- Secret Scene Premiere

Catalog presence does not mean every activity awards ordinary game points.

### `murise.game_versions`

Fields:
- `id`
- `game_definition_id`: FK to game_definitions
- `version`: positive integer
- `summary`
- `rules_markdown`
- `end_game_scoring_summary`
- `participation_mode`: head_to_head, team_solo, all_teams,
  multi_team_pod, individual
- `scoring_handler`: identifier of a reviewed backend handler
- `scoring_config`: jsonb
- `settings_schema`: jsonb
- `result_schema`: jsonb
- `status`: draft, published, archived
- `source_ref`: nullable rulebook reference
- `created_at`, `updated_at`

Constraints:
- `UNIQUE(game_definition_id, version)`
- `UNIQUE(game_definition_id, id)`

Published rule/scoring content is immutable. Changing rules creates a new version.

Allow published versions to become archived without modifying their content.
Archived versions remain available to events already referencing them.

Referenced or previously published versions cannot be deleted.

V1 uses reviewed, hard-coded scoring handlers. Configuration must never
execute arbitrary user-provided code.

JSON schemas validate configurable settings and structured evidence;
they do not replace relational integrity or backend business validation.

---

## 4. Identity, Events and Roster

### `murise.profiles`

Fields:
- `id`: PK and FK to auth.users.id
- `display_name`
- `created_at`, `updated_at`

Supabase Auth manages credentials, signup, login and password recovery.
Application tables do not store passwords.

Spring Boot creates a missing profile idempotently on the first authenticated
application request, using the verified JWT subject.

Profile creation grants no privileged roles or event memberships.

### `murise.platform_role_grants`

Fields:
- `id`
- `user_id`: FK to profiles
- `role`: ADMIN
- `granted_by`: nullable FK to profiles for controlled bootstrap
- `created_at`
- `revoked_at`: nullable
- `revoked_by`: nullable FK to profiles

Constraints:
- Partial unique index on `(user_id, role) WHERE revoked_at IS NULL`.

Only non-revoked grants authorize actions.

The initial ADMIN is assigned through a controlled deployment/bootstrap
operation. Grants and revocations are audited.

### `murise.events`

Fields:
- `id`
- `name`
- `theme`: nullable
- `starts_at`
- `timezone`: IANA timezone name
- `venue`: nullable
- `status`: draft, published, live, completed, archived
- `scoreboard_visibility`: public, hidden, frozen
- `created_by`: FK to profiles
- `created_at`, `updated_at`

`public` scoreboard visibility means visible to the event's authorized
audience. It does not make a private event anonymously accessible.

An anonymous TV display, if implemented, requires its own explicitly
scoped access mechanism.

### `murise.event_memberships`

Fields:
- `id`
- `event_id`: FK to events
- `user_id`: FK to profiles
- `status`: active, suspended, revoked
- `created_at`, `updated_at`

Constraints:
- `UNIQUE(event_id, id)`
- `UNIQUE(event_id, user_id)`

Join requests remain separate until approval.

### `murise.event_role_grants`

Fields:
- `id`
- `event_id`
- `membership_id`
- `role`: ORGANIZER, REFEREE, PLAYER, VIEWER
- `granted_by`: FK to profiles
- `created_at`
- `revoked_at`: nullable
- `revoked_by`: nullable FK to profiles

Constraints:
- Composite FK `(event_id, membership_id)` to event_memberships.
- Partial unique index on `(event_id, membership_id, role)`
  where `revoked_at IS NULL`.

Membership must be active for its grants to authorize actions.

### `murise.referee_assignments`

Fields:
- `id`
- `event_id`
- `membership_id`
- `event_game_id`
- `round_id`: nullable
- `assigned_by`: FK to profiles
- `created_at`
- `revoked_at`: nullable

Constraints:
- Composite FKs to membership, event game and optional round.
- At most one active game-wide assignment per membership/event game.
- At most one active round assignment per membership/round.

A null `round_id` means game-wide authority. Otherwise authority is restricted
to that round.

Spring Boot validates that the round belongs to the assigned event game.
Assignments authorize nothing without an active REFEREE grant.

### `murise.event_invites`

Fields:
- `id`
- `event_id`
- `token_hash`: unique, not null
- `expires_at`
- `revoked_at`: nullable
- `max_uses`: nullable for unlimited use
- `use_count`: integer, default 0
- `created_by`: FK to profiles
- `created_at`

Constraints:
- `UNIQUE(event_id, id)`
- `CHECK (max_uses IS NULL OR max_uses > 0)`
- `CHECK (use_count >= 0)`
- `CHECK (max_uses IS NULL OR use_count <= max_uses)`

Only a hash of the invitation token is stored.

### `murise.join_requests`

Fields:
- `id`
- `event_id`
- `user_id`: FK to profiles
- `invite_id`: nullable
- `status`: pending, approved, rejected, cancelled
- `decided_by`: nullable FK to profiles
- `decided_at`: nullable
- `created_at`, `updated_at`

Constraints:
- Composite FK `(event_id, invite_id)` to event_invites.
- Partial unique index on `(event_id, user_id)` where status is pending.

#### Proposed V1 joining flow — pending confirmation

1. A signed-in account follows an invitation link.
2. The backend validates the invitation and creates a pending request.
3. An organizer approves the request and assigns the participant's team.
4. Approval creates or reactivates membership, grants PLAYER and creates
   or updates the participant in one transaction.

Proposed invitation-use policy:
- A newly created request consumes one use.
- Repeated requests from the same pending applicant consume no additional use.
- Active members consume no additional use.
- Rejection/cancellation does not refund a use.
- Invitation expiry/revocation blocks new requests.
- Existing requests still require explicit organizer approval.

Lock the invitation row while checking capacity and consuming a use.
Lock the request during approval and make repeated approval harmless.

Rate-limit redemption and return no private event content before authorization.

Guest support is optional for V1. A guest display name does not prove
account ownership; linking requires organizer verification.

### `murise.teams`

Fields:
- `id`
- `event_id`
- `name`
- `color`
- `sort_order`
- `active`: default true
- `created_at`, `updated_at`

Constraints:
- `UNIQUE(event_id, id)`
- `UNIQUE(event_id, name)`

### `murise.event_participants`

Fields:
- `id`
- `event_id`
- `user_id`: nullable for unlinked guests
- `display_name`
- `team_id`: nullable
- `status`: invited, active, benched, removed
- `is_captain`: default false
- `created_at`, `updated_at`

Constraints:
- `UNIQUE(event_id, id)`
- `UNIQUE(event_id, user_id)`
- Composite FK `(event_id, user_id)` to
  event_memberships(event_id, user_id).
- Composite FK `(event_id, team_id)` to teams.
- `CHECK (NOT is_captain OR team_id IS NOT NULL)`
- Partial unique index on `(event_id, team_id)`
  where is_captain is true and status is active.

Multiple unlinked guests are allowed because user_id is nullable.

A participant record alone grants no authenticated access.

Reassignment must not rewrite played participation. Until fixture-level
participant history is implemented, prohibit team reassignment after that
participant's team has started play.

---

## 5. Event Lineup, Scheduling and Fixtures

### `murise.event_games`

Fields:
- `id`
- `event_id`
- `game_definition_id`
- `game_version_id`
- `settings_snapshot`: jsonb
- `scoreboard_visibility_override`: nullable; public or hidden
- `house_rules_note`: nullable
- `status`: scheduled, active, settled, voided
- `sort_order`
- `created_at`, `updated_at`

Constraints:
- `UNIQUE(event_id, id)`
- `UNIQUE(event_id, game_definition_id)`
- Composite FK `(game_definition_id, game_version_id)` to
  game_versions(game_definition_id, id).

Select only published versions for new event selections.

Validate settings against the pinned version's settings_schema.
Freeze version/settings once play begins.

House-rule notes do not override executable scoring logic.

### `murise.schedule_versions`

Fields:
- `id`
- `event_id`
- `mode`: balanced, round_robin, manual
- `algorithm_version`
- `seed`: nullable
- `config`: jsonb
- `status`: draft, published, superseded
- `version`
- `created_at`, `updated_at`

Constraints:
- `UNIQUE(event_id, id)`
- `UNIQUE(event_id, version)`
- At most one published schedule per event.

Balanced Game Night validation:
- Each team plays each selected game at most once.
- Eligible head-to-head opponent pairings occur equally often.
- Solo and all-team activities retain their own participation formats.
- No overlapping fixtures for a team.
- Resource capacities and transitions are respected.
- Infeasible exact balance is reported, never silently relaxed.

If exact balance is impossible, show the conflict and require an organizer
to approve a revised setup or explicitly manual schedule.

Round-robin mode may repeat a game across fixtures.

Tournament generation is deferred; fixture dependencies reserve support.

Publishing locks the event and validates the whole schedule transactionally.
Changes must not silently rewrite started or completed fixtures.

### `murise.schedule_slots`

Fields:
- `id`
- `event_id`
- `schedule_version_id`
- `event_game_id`
- `start_at`, `end_at`
- `created_at`, `updated_at`

Constraints:
- Composite FKs to schedule version and event game.
- `CHECK (end_at > start_at)`
- Unique composite target including event_id, schedule_version_id,
  event_game_id and id for fixture references.

### `murise.event_resources`

Fields:
- `id`
- `event_id`
- `type`: room, table, station
- `name`
- `capacity`: positive integer
- `created_at`, `updated_at`

### `murise.slot_resource_assignments`

Fields:
- `event_id`
- `slot_id`
- `resource_id`

Constraints:
- `PRIMARY KEY(slot_id, resource_id)`
- Composite FKs to slot and resource within the same event.

### `murise.fixtures`

Fields:
- `id`
- `event_id`
- `event_game_id`
- `schedule_version_id`
- `slot_id`
- `round_number`
- `status`: scheduled, active, completed, voided
- `created_at`, `updated_at`

Constraints:
- Composite FKs to event game and schedule version.
- Composite FK tying slot_id to the same event, schedule version and event game.

### `murise.fixture_teams`

Fields:
- `event_id`
- `fixture_id`
- `team_id`
- `side`
- `created_at`

Constraints:
- `PRIMARY KEY(fixture_id, team_id)`
- `UNIQUE(event_id, fixture_id, team_id)`
- Composite FKs to fixture and team.

The bridge records fixture participation, independently of current rosters.

### `murise.fixture_dependencies`

Reserved for future tournament progression.

Fields:
- `event_id`
- `source_fixture_id`
- `target_fixture_id`
- `target_side`
- `qualification`

Constraints:
- Composite FKs to source and target fixtures.
- No self-dependency.
- No duplicate dependency.
- Backend validation rejects cycles and conflicting advancement rules.

### `murise.rounds`

Fields:
- `id`
- `event_id`
- `fixture_id`
- `position`
- `kind`
- `status`
- `deadline_at`: nullable
- `version`
- `created_at`, `updated_at`

Constraints:
- Composite FK to fixture.
- `UNIQUE(fixture_id, position)`
- `UNIQUE(event_id, fixture_id, id)`

---

## 6. Results and Points Ledger

### `murise.round_results`

Fields:
- `id`
- `event_id`
- `fixture_id`
- `round_id`
- `team_id`
- `raw_value`: numeric(12,4)
- `outcome`
- `evidence`: jsonb
- `confirmed_by`: nullable FK to profiles
- `revision`
- `created_at`, `updated_at`

Constraints:
- `UNIQUE(round_id, team_id)`
- Composite FK `(event_id, fixture_id, round_id)` to rounds.
- Composite FK `(event_id, fixture_id, team_id)` to fixture_teams.

Including fixture_id allows the database to verify both the round and team
belong to the same fixture.

### `murise.fixture_results`

Fields:
- `id`
- `event_id`
- `fixture_id`
- `team_id`
- `raw_score`: numeric(12,4)
- `raw_max`: nullable numeric(12,4)
- `outcome`: win, loss, draw, placed, dq
- `evidence`: jsonb
- `revision`
- `created_at`, `updated_at`

Constraints:
- `UNIQUE(fixture_id, team_id)`
- Composite FK `(event_id, fixture_id, team_id)` to fixture_teams.

### `murise.game_results`

Fields:
- `id`
- `event_id`
- `event_game_id`
- `team_id`
- `raw_score`: numeric(12,4)
- `raw_max`: nullable numeric(12,4)
- `normalized_points`: numeric(12,4)
- `status`: provisional, final, voided
- `revision`
- `created_at`, `updated_at`

Constraints:
- `UNIQUE(event_game_id, team_id)`
- `UNIQUE(event_id, team_id, id)`
- Composite FKs to event game and team.

The backend validates eligibility and calculates points using the pinned
game version. Clients cannot submit authoritative normalized points.

### `murise.score_entries`

Append-only ledger used to calculate standings.

Fields:
- `id`
- `event_id`
- `team_id`
- `result_id`: nullable
- `guess_id`: nullable; added with the Scene mechanics migration
- `category`: game_points, bonus, penalty, adjustment, reversal
- `delta`: numeric(12,4)
- `source_key`
- `reason`
- `actor_id`: FK to profiles
- `reverses_entry_id`: nullable
- `created_at`

Constraints:
- `UNIQUE(event_id, source_key)`
- `UNIQUE(event_id, team_id, id)`
- `UNIQUE(reverses_entry_id)`
- Composite FK to team.
- Composite FK `(event_id, team_id, result_id)` to game_results.
- Composite FK `(event_id, team_id, guess_id)` to guesses when implemented.
- Composite self-FK `(event_id, team_id, reverses_entry_id)` to score_entries.
- Reversal category requires reverses_entry_id; other categories forbid it.
- An entry cannot reverse itself.

A reversal must negate the full original delta and cannot reverse another
reversal. Enforce these cross-row rules transactionally with locking and,
where appropriate, a database trigger.

Corrections append a reversal and a replacement entry in one transaction.
Original entries remain unchanged.

Result projections may change through controlled, revisioned corrections.
Ledger entries never change.

Every scoring handler defines which points come from game settlement and
which come from direct bonuses so the same award is never counted twice.

---

## 7. Game-Specific Persistence

| Activity | Persistence |
|---|---|
| Cup, Poison, Grid, Sensory | Shared fixtures, rounds and results with validated game-specific evidence. |
| Rehearsal | Schedule and live state; no scoring entries. |
| Group Chat | Shared results; optional private content assignments if that variant is confirmed. |
| Secret Scene Premiere | Dedicated entries, clues, releases, guesses and reviews. |
| Unified Alibi | Dedicated cases, questions, assignments, answers and verdicts. |
| Social Scenario | Dedicated cases, private rules, turns and observations. |

Planned mechanics tables:

- Scene: scene_entries, scene_aliases, scene_clues, guessing_state,
  clue_releases, guesses, guess_reviews.
- Alibi: alibi_case_versions, alibi_questions, alibi_rounds,
  alibi_assignments, alibi_prompts, alibi_answers, alibi_verdicts.
- Social: social_case_versions, social_rules, social_matches,
  social_turns, social_observations.
- Optional Group Chat content: event_content_assignments.

These are planned table boundaries, not complete SQL definitions.
Define columns, constraints and authorization with each game's implementation.

All event-owned mechanics relationships follow the composite-FK conventions.
Guesses used as ledger sources expose `UNIQUE(event_id, team_id, id)`.

The designated master rulebook governs game behavior. Unresolved rule
conflicts remain explicit and must not be replaced with invented defaults.

Private answers and unreleased content must not appear in public rulebook fields.

---

## 8. Live State, Visibility, Idempotency and Media

### `murise.event_state`

Fields:
- `event_id`: PK and FK to events
- `current_event_game_id`: nullable
- `phase`
- `deadline_at`: nullable
- `paused_remaining_ms`: nullable
- `version`
- `updated_at`

Composite FK `(event_id, current_event_game_id)` to event_games.

The backend owns phase transitions and deadlines.
Frontend countdowns display backend state.

### Scoreboard visibility

- `public`: expose permitted current standings.
- `hidden`: omit score totals from player/viewer responses.
- `frozen`: expose a stored snapshot while calculation continues.

Global hidden/frozen modes take precedence over per-game public overrides.
Within public mode, a game override may hide that game's contribution.
Hidden contributions must not leak through aggregate totals.

Freezing requires a consistent snapshot, not merely a status flag.

### `murise.scoreboard_snapshots`

Fields:
- `id`
- `event_id`
- `created_by`: FK to profiles
- `created_at`

### `murise.scoreboard_snapshot_rows`

Fields:
- `event_id`
- `snapshot_id`
- `team_id`
- `points`: numeric(12,4)
- `rank`

Constraints:
- `PRIMARY KEY(snapshot_id, team_id)`
- Composite FKs to snapshot and team.

Add nullable `events.frozen_snapshot_id` with a composite FK to the snapshot.

Create the snapshot and activate frozen visibility in one transaction
serialized with event scoring operations.

Snapshots contain only information authorized for the intended audience.

### `murise.command_receipts`

Fields:
- `id`
- `event_id`
- `actor_id`: FK to profiles
- `command_type`
- `idempotency_key`
- `request_hash`
- `response`
- `created_at`

Constraints:
- `UNIQUE(event_id, actor_id, command_type, idempotency_key)`

Rules:
- Verify current authorization before returning a cached response.
- The same key with a different request hash returns a conflict.
- Persist the business change and completed receipt in one transaction.
- Resolve concurrent duplicate requests through the unique constraint
  and transaction handling.
- Cached responses must not bypass newly revoked permissions or visibility.

### `murise.audit_events`

Fields:
- `id`
- `event_id`: nullable for platform-wide actions
- `actor_id`: nullable only for identified system/bootstrap actions
- `action`
- `entity_type`
- `entity_id`: nullable
- `details`: jsonb
- `created_at`

Append-only. Do not store passwords, access tokens or invitation tokens.

### `murise.media_assets`

Fields:
- `id`
- `event_id`
- `owner_id`: FK to profiles
- `bucket`
- `object_path`
- `mime_type`
- `byte_size`
- `status`
- `created_at`, `updated_at`

Constraints:
- `UNIQUE(bucket, object_path)`
- `CHECK (byte_size >= 0)`

Private files remain in private storage.
Spring Boot authorizes uploads/downloads and issues short-lived access
where appropriate. Storage service credentials never reach the browser.

---

## 9. Security, Migrations and Transactions

### Database access

- `postgres` owns the murise schema.
- `murise_migrator` manages application DDL and Flyway history.
- `murise_app` receives only required runtime privileges.
- The browser does not access murise tables through Supabase PostgREST.
- Restrict schema access for PUBLIC, anon and authenticated.
- Default privileges must apply to objects created by murise_migrator.
- Runtime access must not include mutation of Flyway schema history.
- Append-only tables prohibit runtime UPDATE, DELETE and TRUNCATE.
- Runtime DDL is forbidden.
- Hibernate uses ddl-auto=validate; Flyway creates and evolves the schema.

Ordinary JDBC sessions do not automatically inherit the authenticated
Supabase user's identity. Spring Boot must enforce account/event permissions.

### Authentication and authorization

- Verify JWT signature, issuer, audience and expiry.
- Resolve account identity from the verified subject.
- Read current platform grants, event membership and event grants.
- Enforce referee scope and team/captain checks where applicable.
- Validate ownership and event scope for every referenced identifier.
- Exclude private fields before serialization.
- Do not cache private responses in shared public caches.

### Transaction boundaries

Use transactions and consistent locking for:
- Event creation and first organizer assignment.
- Organizer removal, membership suspension and revocation.
- Invitation capacity consumption.
- Join approval and team assignment.
- Schedule publication.
- Result settlement, reversal and correction.
- Phase advancement.
- Scoreboard freezing.

All operations affecting the same invariant must follow the same locking
protocol. Idempotency does not replace authorization or locking.

### Test environment

CI uses disposable PostgreSQL and test-only credentials.

Because plain PostgreSQL does not include Supabase auth.users:
- Provide a minimal test-only auth.users fixture before application migrations,
  or run a local Supabase test environment.
- Never create a replacement auth schema inside production application migrations.
- Verify the restricted migrator has the permissions needed for auth.users
  foreign keys in the hosted environment.

---

## 10. Implementation Order

### GM-007: Database foundation

- Implement core identity, event, membership, role and roster migrations.
- Add required unique constraints and composite foreign keys.
- Verify migration ownership and restricted runtime privileges.
- Test duplicate memberships and cross-event relationship rejection.

### GM-008: Authentication and joining

- Signup, login and password recovery.
- Idempotent profile creation.
- Valid, invalid and expired token tests.
- Confirm the V1 invitation policy.
- Implement request/approval/team assignment transactions.
- Verify duplicate approval and concurrent invitation use.
- Verify recovery preserves the same account and participation.

### GM-009: Permissions

- Platform ADMIN bootstrap and audited intervention.
- Organizer delegation and revocation.
- Referee assignments.
- Last-organizer protection under concurrent requests.
- Current authorization checks before idempotent response replay.
- Event and private-content access tests.

### Subsequent cards

- Event creation and catalog selection.
- Shared fixtures, results and score ledger.
- Balanced scheduling and manual fallback.
- Game-specific private mechanics.
- Live display, recovery and rehearsal.

Core authentication can begin before the complete game schema exists.
Event joining requires the core migrations and organizer permission checks.