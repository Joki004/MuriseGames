-- V18__add_team_results_visibility.sql
-- What a team sees about its own results on the Player screen, chosen by the host:
--   own_results      own matches only (opponent, won/lost, score per round)
--   own_points       own matches + own points per game and total
--   full_scoreboard  full live scoreboard on players' phones
-- The existing scoreboard_visibility keeps controlling the public leaderboard (TV/overview).

ALTER TABLE murise.events
    ADD COLUMN team_results_visibility text NOT NULL DEFAULT 'own_results'
        CHECK (team_results_visibility IN ('own_results', 'own_points', 'full_scoreboard'));
