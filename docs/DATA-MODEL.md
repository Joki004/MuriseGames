# Data model: shared structure, game-specific rules

Use **one games table, one row per catalogue game**. Do not create nine copies of events, scores,
matches or teams. New games will be rows plus a supported scoring implementation/configuration.

## Implemented in this starter

`murise.games(code varchar(80) PK, name varchar(160) NOT NULL, rules_status varchar(40) NOT NULL)`.
Status is `PENDING_REVIEW` or `APPROVED`. Flyway seeds nine working labels; confirm final display names
against the master book. No game-specific rule parameters or points are invented here.

## Proposed next migrations (not implemented yet)

| Table | Main columns / relationships |
| --- | --- |
| `profiles` | `id` UUID matching Supabase auth subject, `display_name`, timestamps |
| `platform_roles` | `user_id` FK profiles, role; global admin grants controlled by backend |
| `events` | UUID id, title, theme, starts_at, timezone, status, owner_id FK profiles, version |
| `event_memberships` | event_id + user_id unique, event role (`ORGANISER`, `PARTICIPANT`) |
| `teams` | UUID id, event_id FK events, name; unique name within event |
| `team_memberships` | event_id, team_id, user_id; enforce at most one team per user/event |
| `event_games` | UUID id, event_id, game_code FK games, approved rules version, configuration JSONB |
| `schedule_versions` | UUID id, event_id, format, status, revision, created_by |
| `matches` | UUID id, schedule_version_id, event_game_id, round, room/station, start/end, status |
| `match_teams` | match_id + team_id, participant position; supports more than two teams |
| `score_entries` | UUID id, event_id, match_id, team_id, points, metrics JSONB, reason, actor, timestamp, idempotency key |

Prevent cross-event references with composite foreign keys/constraints, not only application checks.
Use immutable score entries and compensating corrections with a reason. Derive leaderboards from
the ledger; don't maintain a second editable total. Store times as `timestamptz` and the event's
IANA timezone separately. Keep event roles separate from global platform roles.

`JSONB` can hold validated game-specific measurements (time, counts, judge marks); it is not a
replacement for foreign keys, team relationships or server validation. Version scoring rules and
snapshot the selected version/configuration on an event so editing the catalogue cannot change old results.
Specialized clue/secret tables may be added for games that need them; do not send host secrets in player DTOs.

## Scheduling boundaries

V1 prioritizes the game's station rotation, with manual review before publishing. Round robin and
elimination tournaments are additional strategies. Equal opponent frequency AND at most one visit
per game cannot be guaranteed for every team/game count: validate feasibility and report unmet
constraints; never label an approximate rotation perfectly balanced. Solo, all-team and unscored
activities need their own slots rather than being forced into head-to-head matches.
