package com.murisegames.api.modules.Event.Schedule;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class BalancedScheduleGeneratorTest {

    @Test
    void fourTeamsAcrossThreeHeadToHeadGamesFaceEveryOpponentOnce() {
        List<UUID> teams = List.of(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()
        );
        List<BalancedScheduleGenerator.GameInput> games = List.of(
                new BalancedScheduleGenerator.GameInput(UUID.randomUUID(), "Grab the Cup", "head_to_head"),
                new BalancedScheduleGenerator.GameInput(UUID.randomUUID(), "Poison Game", "head_to_head"),
                new BalancedScheduleGenerator.GameInput(UUID.randomUUID(), "Rhythm Grid Hunt", "head_to_head")
        );

        var result = BalancedScheduleGenerator.generate(
                teams, games, Instant.parse("2026-11-28T19:00:00Z"), 20, 5, 42L
        );

        assertThat(result.slots()).hasSize(6);
        assertThat(result.slots()).allMatch(s -> !s.bye() && s.teamIds().size() == 2);

        // Every pair meets at most once across 3 games with 4 teams
        assertNoRematches(teams, result);
    }

    @Test
    void fourTeamsNeverRematchRegardlessOfSeed() {
        // Regression: parallel games in the same window used to lock a game into a
        // forced rematch (e.g. A plays 0v1 while B plays 2v3 -> A's last match is 2v3 again).
        for (long seed = 1; seed <= 50; seed++) {
            List<UUID> teams = List.of(
                    UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()
            );
            List<BalancedScheduleGenerator.GameInput> games = List.of(
                    new BalancedScheduleGenerator.GameInput(UUID.randomUUID(), "Grab the Cup", "head_to_head"),
                    new BalancedScheduleGenerator.GameInput(UUID.randomUUID(), "Poison Game", "head_to_head"),
                    new BalancedScheduleGenerator.GameInput(UUID.randomUUID(), "Rhythm Grid Hunt", "head_to_head")
            );

            var result = BalancedScheduleGenerator.generate(
                    teams, games, Instant.parse("2026-11-28T19:00:00Z"), 20, 5, seed
            );

            assertThat(result.slots()).as("seed %d", seed).hasSize(6);
            assertNoRematches(teams, result);
        }
    }

    @Test
    void oddTeamsGetAnExtraMatchInsteadOfAByeAndSoloGamesDoNotAddOpponents() {
        List<UUID> teams = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        List<BalancedScheduleGenerator.GameInput> games = List.of(
                new BalancedScheduleGenerator.GameInput(UUID.randomUUID(), "Grab the Cup", "head_to_head"),
                new BalancedScheduleGenerator.GameInput(UUID.randomUUID(), "Sensory Chain", "team_solo"),
                new BalancedScheduleGenerator.GameInput(UUID.randomUUID(), "Final Rehearsal", "all_teams")
        );

        var result = BalancedScheduleGenerator.generate(
                teams, games, Instant.parse("2026-11-28T19:00:00Z"), 20, 0, 7L
        );

        long byeCount = result.slots().stream().filter(BalancedScheduleGenerator.PlannedSlot::bye).count();
        assertThat(byeCount).isZero();
        assertThat(result.slots()).hasSize(6); // 2 matches + 3 solo + 1 all_teams

        UUID grabTheCup = games.getFirst().eventGameId();
        var matches = result.slots().stream()
                .filter(s -> s.eventGameId().equals(grabTheCup))
                .toList();
        assertThat(matches).hasSize(2).allMatch(s -> s.teamIds().size() == 2);

        // Every team plays at least once; exactly one team plays twice.
        Map<UUID, Long> appearances = appearancesIn(matches, teams);
        assertThat(appearances.values()).allMatch(n -> n >= 1 && n <= 2);
        assertThat(appearances.values().stream().filter(n -> n == 2)).hasSize(1);
        assertThat(result.warnings()).anyMatch(w -> w.contains("second match instead of a bye"));
    }

    @Test
    void oddTeamCountsNeverProduceByesAcrossSeeds() {
        for (int teamCount : new int[]{3, 5, 7}) {
            for (long seed = 1; seed <= 40; seed++) {
                List<UUID> teams = new ArrayList<>();
                for (int i = 0; i < teamCount; i++) teams.add(UUID.randomUUID());
                List<BalancedScheduleGenerator.GameInput> games = List.of(
                        new BalancedScheduleGenerator.GameInput(UUID.randomUUID(), "Grab the Cup", "head_to_head"),
                        new BalancedScheduleGenerator.GameInput(UUID.randomUUID(), "Poison Game", "head_to_head"),
                        new BalancedScheduleGenerator.GameInput(UUID.randomUUID(), "Rhythm Grid Hunt", "head_to_head"),
                        new BalancedScheduleGenerator.GameInput(UUID.randomUUID(), "Unified Alibi", "team_solo")
                );

                var result = BalancedScheduleGenerator.generate(
                        teams, games, Instant.parse("2026-11-28T19:00:00Z"), 20, 5, seed, 2
                );

                assertThat(result.slots()).noneMatch(BalancedScheduleGenerator.PlannedSlot::bye);
                assertNoDoubleBooking(result);

                for (var game : games.subList(0, 3)) {
                    var matches = result.slots().stream()
                            .filter(s -> s.eventGameId().equals(game.eventGameId()))
                            .toList();
                    String label = teamCount + " teams, seed " + seed + ", " + game.name();

                    assertThat(matches).as(label).hasSize((teamCount + 1) / 2);
                    assertThat(matches.stream().map(s -> Set.copyOf(s.teamIds())).distinct())
                            .as("%s: a pair repeats inside one game", label)
                            .hasSize(matches.size());

                    Map<UUID, Long> appearances = appearancesIn(matches, teams);
                    assertThat(appearances.values()).as(label).allMatch(n -> n >= 1 && n <= 2);
                    assertThat(appearances.values().stream().filter(n -> n == 2))
                            .as(label).hasSize(1);
                }
            }
        }
    }

    @Test
    void theTeamPlayingTwiceRotatesBetweenGames() {
        List<UUID> teams = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        List<BalancedScheduleGenerator.GameInput> games = List.of(
                new BalancedScheduleGenerator.GameInput(UUID.randomUUID(), "Grab the Cup", "head_to_head"),
                new BalancedScheduleGenerator.GameInput(UUID.randomUUID(), "Poison Game", "head_to_head"),
                new BalancedScheduleGenerator.GameInput(UUID.randomUUID(), "Rhythm Grid Hunt", "head_to_head")
        );

        for (long seed = 1; seed <= 40; seed++) {
            var result = BalancedScheduleGenerator.generate(
                    teams, games, Instant.parse("2026-11-28T19:00:00Z"), 20, 5, seed
            );

            Map<UUID, Integer> gamesPlayedTwice = new HashMap<>();
            for (var game : games) {
                var matches = result.slots().stream()
                        .filter(s -> s.eventGameId().equals(game.eventGameId()))
                        .toList();
                appearancesIn(matches, teams).forEach((team, n) -> {
                    if (n == 2) gamesPlayedTwice.merge(team, 1, Integer::sum);
                });
            }

            // 3 teams, 3 games: each team plays twice in exactly one game.
            assertThat(gamesPlayedTwice).as("seed %d", seed).hasSize(3);
            assertThat(gamesPlayedTwice.values()).as("seed %d", seed).allMatch(n -> n == 1);
        }
    }

    @Test
    void packsSixTeamsAcrossMixedGamesWithoutDoubleBookingOrLongRests() {
        List<UUID> teams = List.of(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()
        );
        List<BalancedScheduleGenerator.GameInput> games = List.of(
                new BalancedScheduleGenerator.GameInput(UUID.randomUUID(), "Poison Game", "head_to_head"),
                new BalancedScheduleGenerator.GameInput(UUID.randomUUID(), "Grab the Cup", "head_to_head"),
                new BalancedScheduleGenerator.GameInput(UUID.randomUUID(), "Duel", "head_to_head"),
                new BalancedScheduleGenerator.GameInput(UUID.randomUUID(), "Unified Alibi", "team_solo"),
                new BalancedScheduleGenerator.GameInput(UUID.randomUUID(), "Sensory Chain", "team_solo"),
                new BalancedScheduleGenerator.GameInput(UUID.randomUUID(), "Final Rehearsal", "all_teams")
        );

        var result = BalancedScheduleGenerator.generate(
                teams, games, Instant.parse("2026-11-28T18:00:00Z"), 15, 5, 42L, null
        );

        Map<Instant, Set<UUID>> seenPerWindow = new HashMap<>();
        for (var slot : result.slots()) {
            if (slot.bye()) continue;
            Set<UUID> seen = seenPerWindow.computeIfAbsent(slot.startAt(), k -> new HashSet<>());
            for (UUID teamId : slot.teamIds()) {
                assertThat(seen.add(teamId))
                        .as("Team %s double-booked at %s", teamId, slot.startAt())
                        .isTrue();
            }
        }

        Instant firstWindow = result.slots().getFirst().startAt();
        long distinctGamesInFirstWindow = result.slots().stream()
                .filter(s -> s.startAt().equals(firstWindow))
                .map(BalancedScheduleGenerator.PlannedSlot::eventGameId)
                .distinct()
                .count();
        assertThat(distinctGamesInFirstWindow).isGreaterThanOrEqualTo(3);
    }

    @Test
    void completeRotationCoversEveryPairOnceIncludingOddTeamCounts() {
        for (int teamCount = 3; teamCount <= 8; teamCount++) {
            List<UUID> teams = new ArrayList<>();
            for (int index = 0; index < teamCount; index++) {
                teams.add(new UUID(0L, index + 1L));
            }

            int gameCount = teamCount % 2 == 0
                    ? teamCount - 1
                    : teamCount;

            List<BalancedScheduleGenerator.GameInput> games = new ArrayList<>();
            for (int index = 0; index < gameCount; index++) {
                games.add(new BalancedScheduleGenerator.GameInput(
                        new UUID(1L, index + 1L),
                        "Game " + (index + 1),
                        "head_to_head"
                ));
            }

            var result = BalancedScheduleGenerator.generate(
                    teams,
                    games,
                    Instant.parse("2026-11-28T18:00:00Z"),
                    15,
                    5,
                    42L,
                    2
            );

            // Check actual scheduled matches, independently of opponentMatrix.
            Set<Set<UUID>> playedPairs = new HashSet<>();
            boolean odd = teamCount % 2 != 0;

            for (var slot : result.slots()) {
                assertThat(slot.bye()).as("No byes with %d teams", teamCount).isFalse();
                assertThat(slot.teamIds()).hasSize(2);
                boolean firstMeeting = playedPairs.add(Set.copyOf(slot.teamIds()));
                if (!odd) {
                    assertThat(firstMeeting)
                            .as("Repeated scheduled pair with %d teams", teamCount)
                            .isTrue();
                }
            }

            // The rotation still covers every pair.
            assertThat(playedPairs)
                    .hasSize(teamCount * (teamCount - 1) / 2);

            // Even: every team plays each game exactly once.
            // Odd: every team plays each game once, except one team that plays twice.
            for (var game : games) {
                var matches = result.slots().stream()
                        .filter(slot -> slot.eventGameId().equals(game.eventGameId()))
                        .toList();
                Map<UUID, Long> appearances = appearancesIn(matches, teams);

                assertThat(appearances.values())
                        .as("Appearances in game %s with %d teams", game.name(), teamCount)
                        .allMatch(n -> n == 1 || (odd && n == 2));
                assertThat(appearances.values().stream().filter(n -> n == 2))
                        .hasSize(odd ? 1 : 0);
            }

            if (!odd) {
                assertNoRematches(teams, result);
            } else {
                // The extra matches repeat a pairing; no pair meets more than twice.
                for (UUID a : teams) {
                    for (UUID b : teams) {
                        if (!a.equals(b)) {
                            assertThat(result.opponentMatrix().get(a).getOrDefault(b, 0))
                                    .isLessThanOrEqualTo(2);
                        }
                    }
                }
            }
        }
    }

    private static Map<UUID, Long> appearancesIn(
            List<BalancedScheduleGenerator.PlannedSlot> slots,
            List<UUID> teams
    ) {
        Map<UUID, Long> appearances = new HashMap<>();
        for (UUID teamId : teams) {
            appearances.put(teamId, slots.stream()
                    .filter(s -> s.teamIds().contains(teamId))
                    .count());
        }
        return appearances;
    }

    private static void assertNoDoubleBooking(BalancedScheduleGenerator.GenerationResult result) {
        Map<Instant, Set<UUID>> seenPerWindow = new HashMap<>();
        for (var slot : result.slots()) {
            Set<UUID> seen = seenPerWindow.computeIfAbsent(slot.startAt(), k -> new HashSet<>());
            for (UUID teamId : slot.teamIds()) {
                assertThat(seen.add(teamId))
                        .as("Team %s double-booked at %s", teamId, slot.startAt())
                        .isTrue();
            }
        }
    }

    private static void assertNoRematches(List<UUID> teams, BalancedScheduleGenerator.GenerationResult result) {
        for (UUID a : teams) {
            for (UUID b : teams) {
                if (!a.equals(b)) {
                    assertThat(result.opponentMatrix().get(a).getOrDefault(b, 0))
                            .as("Pair %s / %s met more than once", a, b)
                            .isLessThanOrEqualTo(1);
                }
            }
        }
    }

}