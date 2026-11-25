package com.murisegames.api.modules.Event.PlayerPoints;

import com.murisegames.api.modules.Event.PlayerPoints.PlayerPointsTypes.Credit;
import com.murisegames.api.modules.Event.PlayerPoints.PlayerPointsTypes.LiveAward;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerPointsRulesTest {

    private final UUID ana = UUID.randomUUID();
    private final UUID ben = UUID.randomUUID();
    private final UUID cyd = UUID.randomUUID();

    @Test
    void segmentsFollowTheScoringFieldsAndSkipUnscoredActivities() {
        assertEquals(List.of("Duel 1", "Duel 2", "Duel 3", "Duel 4"),
                PlayerPointsRules.segmentsFor("grab_the_cup_v1"));
        assertEquals(List.of("Pair 1", "Pair 2"),
                PlayerPointsRules.segmentsFor("unified_alibi_v1"));
        assertEquals(List.of("Round 1", "Round 2"),
                PlayerPointsRules.segmentsFor("social_scenario_v1"));
        // A game without a scoring adapter yet is credited per match.
        assertEquals(List.of("Match"), PlayerPointsRules.segmentsFor("future_game_v1"));
        assertEquals(List.of(), PlayerPointsRules.segmentsFor("unscored_rehearsal_v1"));
        assertEquals(List.of(), PlayerPointsRules.segmentsFor("secret_scene_premiere_v1"));
    }

    @Test
    void validateRejectsStrangersAndUnknownSegmentsAndRemovesDuplicates() {
        Set<UUID> eligible = Set.of(ana, ben);

        List<Credit> ok = PlayerPointsRules.validate(
                List.of(new Credit(0, ana), new Credit(0, ana), new Credit(3, ben)),
                4, eligible);
        assertEquals(List.of(new Credit(0, ana), new Credit(3, ben)), ok);

        assertThrows(IllegalArgumentException.class, () ->
                PlayerPointsRules.validate(List.of(new Credit(0, cyd)), 4, eligible));
        assertThrows(IllegalArgumentException.class, () ->
                PlayerPointsRules.validate(List.of(new Credit(4, ana)), 4, eligible));
        assertThrows(IllegalArgumentException.class, () ->
                PlayerPointsRules.validate(List.of(new Credit(-1, ana)), 4, eligible));
        assertThrows(IllegalArgumentException.class, () ->
                PlayerPointsRules.validate(null, 4, eligible));
    }

    @Test
    void validateCapsPlayersPerSegment() {
        List<UUID> many = new ArrayList<>();
        for (int i = 0; i <= PlayerPointsRules.MAX_CREDITS_PER_SEGMENT; i++) {
            many.add(UUID.randomUUID());
        }
        List<Credit> credits = many.stream().map(id -> new Credit(0, id)).toList();

        assertThrows(IllegalArgumentException.class, () ->
                PlayerPointsRules.validate(credits, 1, Set.copyOf(many)));
    }

    @Test
    void diffKeepsUnchangedCreditsAndReversesRemovedOnes() {
        LiveAward anaDuel1 = new LiveAward(UUID.randomUUID(), 0, ana);
        LiveAward benDuel2 = new LiveAward(UUID.randomUUID(), 1, ben);

        PlayerPointsRules.Diff diff = PlayerPointsRules.diff(
                List.of(anaDuel1, benDuel2),
                List.of(new Credit(0, ana), new Credit(1, cyd)));

        assertEquals(List.of(new Credit(1, cyd)), diff.toAward());
        assertEquals(List.of(benDuel2), diff.toReverse());
    }

    @Test
    void savingTheSameCreditsTwiceChangesNothing() {
        LiveAward anaDuel1 = new LiveAward(UUID.randomUUID(), 0, ana);

        assertTrue(PlayerPointsRules.diff(List.of(anaDuel1), List.of(new Credit(0, ana))).isEmpty());
        assertTrue(PlayerPointsRules.diff(List.of(), List.of()).isEmpty());
    }

    @Test
    void duplicateLiveAwardsAreCleanedUp() {
        LiveAward first = new LiveAward(UUID.randomUUID(), 0, ana);
        LiveAward copy = new LiveAward(UUID.randomUUID(), 0, ana);

        PlayerPointsRules.Diff diff = PlayerPointsRules.diff(
                List.of(first, copy), List.of(new Credit(0, ana)));

        assertEquals(List.of(), diff.toAward());
        assertEquals(List.of(copy), diff.toReverse());
    }

    @Test
    void ranksShareTiesAndSkipAfterThem() {
        List<Integer> points = List.of(5, 3, 3, 1);
        assertArrayEquals(new int[]{1, 2, 2, 4}, PlayerPointsRules.ranks(points, p -> p));
        assertArrayEquals(new int[]{}, PlayerPointsRules.ranks(List.<Integer>of(), p -> p));
    }
}
