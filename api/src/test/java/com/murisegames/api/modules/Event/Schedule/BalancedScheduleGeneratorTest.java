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
    void oddTeamsProduceExplicitByeAndSoloGamesDoNotAddOpponents() {
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
        assertThat(byeCount).isEqualTo(1);
        assertThat(result.slots()).hasSize(6); // 1 match + 1 bye + 3 solo + 1 all_teams
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

            for (var slot : result.slots()) {
                if (slot.bye()) {
                    assertThat(slot.teamIds()).hasSize(1);
                    continue;
                }

                assertThat(slot.teamIds()).hasSize(2);
                assertThat(playedPairs.add(Set.copyOf(slot.teamIds())))
                        .as("Repeated scheduled pair with %d teams", teamCount)
                        .isTrue();
            }

            assertThat(playedPairs)
                    .hasSize(teamCount * (teamCount - 1) / 2);

            long expectedByes = teamCount % 2 == 0 ? 0L : teamCount;

            assertThat(result.slots().stream()
                    .filter(BalancedScheduleGenerator.PlannedSlot::bye)
                    .count())
                    .isEqualTo(expectedByes);

            // Every team gets exactly one assignment per game:
            // either a real match or its explicit bye.
            for (var game : games) {
                for (UUID teamId : teams) {
                    long appearances = result.slots().stream()
                            .filter(slot ->
                                    slot.eventGameId().equals(game.eventGameId()))
                            .filter(slot -> slot.teamIds().contains(teamId))
                            .count();

                    assertThat(appearances)
                            .as("Team %s in game %s", teamId, game.name())
                            .isEqualTo(1L);
                }
            }

            assertNoRematches(teams, result);
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