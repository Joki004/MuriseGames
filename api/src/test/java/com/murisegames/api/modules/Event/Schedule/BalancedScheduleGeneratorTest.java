package com.murisegames.api.modules.Event.Schedule;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

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
        for (UUID a : teams) {
            for (UUID b : teams) {
                if (!a.equals(b)) {
                    assertThat(result.opponentMatrix().get(a).get(b)).isLessThanOrEqualTo(1);
                }
            }
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
}