package com.murisegames.api.modules.Event.Scoring.Adapter;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Rules decided 25 Nov 2026: 2 rounds, max 6 per round, 5 × raw ÷ 12. */
class SocialScoringDecisionTest {

    @Test
    void socialIsHeadToHeadWithIndependentScoresOutOfTwelve() {
        GameScoreAdapters.Definition social = GameScoreAdapters.find("social_scenario_v1");

        assertNotNull(social);
        assertTrue(social.headToHead());
        assertFalse(social.complementary());
        assertEquals(List.of("Round 1", "Round 2"), social.fields());
        assertEquals(6, social.maximumPerField());
        assertEquals(12, social.maximumPerFixture());
    }

    @Test
    void duelGamesStayComplementary() {
        assertTrue(GameScoreAdapters.find("grab_the_cup_v1").complementary());
        assertTrue(GameScoreAdapters.find("poison_game_v1").complementary());
        assertTrue(GameScoreAdapters.find("rhythm_grid_hunt_v1").complementary());
        assertFalse(GameScoreAdapters.find("group_chat_meltdown_v1").complementary());
    }
}
