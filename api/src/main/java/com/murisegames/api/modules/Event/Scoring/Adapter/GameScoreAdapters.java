package com.murisegames.api.modules.Event.Scoring.Adapter;

import com.murisegames.api.modules.Catalog.GameVersion;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public final class GameScoreAdapters {

    private GameScoreAdapters() {
    }

    public record Definition(
            String handler,
            boolean headToHead,
            List<String> fields,
            int maximumPerField,
            String inputHint,
            String decisionNote,
            Map<String, Integer> requiredConfig,
            /**
             * Head-to-head games whose two teams' values per field always total
             * maximumPerField (a duel won by one side). Social Scenario is
             * head-to-head but both teams score independently.
             */
            boolean complementary
    ) {
        public Definition(
                String handler,
                boolean headToHead,
                List<String> fields,
                int maximumPerField,
                String inputHint,
                String decisionNote,
                Map<String, Integer> requiredConfig
        ) {
            this(handler, headToHead, fields, maximumPerField, inputHint,
                    decisionNote, requiredConfig, headToHead);
        }

        public int maximumPerFixture() {
            return fields.size() * maximumPerField;
        }

        public String configurationProblem(GameVersion version) {
            Map<String, Object> config = version.getScoringConfig();

            if (config == null) {
                return "This rule version has no scoring configuration.";
            }

            for (var expected : requiredConfig.entrySet()) {
                Object actual = config.get(expected.getKey());

                try {
                    if (actual == null
                            || new BigDecimal(actual.toString()).compareTo(
                            BigDecimal.valueOf(expected.getValue())
                    ) != 0) {
                        return "This rule version changes "
                                + expected.getKey()
                                + ". Its scoring adapter needs review.";
                    }
                } catch (NumberFormatException exception) {
                    return "The scoring configuration contains an invalid "
                            + expected.getKey() + " value.";
                }
            }

            return null;
        }
    }

    private static final Map<String, Definition> DEFINITIONS = Map.of(
            "social_scenario_v1",
            new Definition(
                    "social_scenario_v1",
                    true,
                    List.of("Round 1", "Round 2"),
                    6,
                    "Enter each team's points per round. As defender: +1 per "
                            + "goal, +1 more if all 3 goals with no red flag. "
                            + "As attacker: +2 for a red flag. 0 to 6 per round.",
                    "Decided 25 Nov 2026: 180 s turns, 2 rounds (no 3rd), "
                            + "red flag beats a simultaneous goal, timeout keeps "
                            + "goals without bonus; points = 5 × raw ÷ 12.",
                    Map.of(
                            "goals", 3,
                            "red_flags", 2,
                            "max_normalized", 5
                    ),
                    false
            ),

            "grab_the_cup_v1",
            new Definition(
                    "grab_the_cup_v1",
                    true,
                    List.of("Duel 1", "Duel 2", "Duel 3", "Duel 4"),
                    1,
                    "Enter 1 for a duel won and 0 for a duel lost.",
                    "A round-robin denominator includes four duels "
                            + "for every scheduled opponent.",
                    Map.of(
                            "duels", 4,
                            "attempt_wins", 2,
                            "max_raw", 4,
                            "max_normalized", 5
                    )
            ),

            "sensory_chain_v1",
            new Definition(
                    "sensory_chain_v1",
                    false,
                    List.of("Turn 1", "Turn 2", "Turn 3", "Turn 4"),
                    3,
                    "Enter the number of correct cards in each turn.",
                    "Preview follows the four-turn, three-card configuration.",
                    Map.of(
                            "turns", 4,
                            "cards_per_turn", 3,
                            "max_raw", 12,
                            "max_normalized", 5
                    )
            ),

            "unified_alibi_v1",
            new Definition(
                    "unified_alibi_v1",
                    false,
                    List.of("Pair 1", "Pair 2"),
                    10,
                    "Enter the number of answers accepted as matching "
                            + "by the judge for each pair.",
                    "The judge decides semantic matches. Played on phones in the "
                            + "app (GM-046): typed answers, 30 s per question, recorded "
                            + "from the Alibi game page.",
                    Map.of(
                            "pairs", 2,
                            "questions_per_pair", 10,
                            "max_raw", 20,
                            "max_normalized", 5
                    )
            ),

            "poison_game_v1",
            new Definition(
                    "poison_game_v1",
                    true,
                    List.of("Round 1", "Round 2"),
                    1,
                    "Enter 1 for a round won and 0 for a round lost.",
                    "Decided 25 Nov 2026: 6 cups; no choice within 5 s and the "
                            + "host picks a random remaining cup; an exposed "
                            + "marker means reshuffle and replay the round.",
                    Map.of(
                            "rounds", 2,
                            "max_normalized", 5
                    )
            ),

            "rhythm_grid_hunt_v1",
            new Definition(
                    "rhythm_grid_hunt_v1",
                    true,
                    List.of(
                            "Round 1 · first turn",
                            "Round 1 · second turn",
                            "Round 2 · first turn",
                            "Round 2 · second turn",
                            "Round 3 · first turn",
                            "Round 3 · second turn",
                            "Round 4 · first turn",
                            "Round 4 · second turn"
                    ),
                    3,
                    "Enter catches when this team hunts, or survivors "
                            + "when this team runs. Opposing scores in "
                            + "the same turn must total 3.",
                    "Decided 25 Nov 2026: one shared 3×3 grid; 24-point "
                            + "fixture maximum approved.",
                    Map.of(
                            "rounds", 4,
                            "max_raw", 24,
                            "max_normalized", 5
                    )
            ),

            "group_chat_meltdown_v1",
            new Definition(
                    "group_chat_meltdown_v1",
                    false,
                    List.of("Round 1", "Round 2", "Round 3", "Round 4"),
                    1,
                    "Enter 1 when the situation was guessed correctly "
                            + "and 0 otherwise.",
                    "Decided 25 Nov 2026: the secret-situation version "
                            + "(slide 12), not the voting variant.",
                    Map.of(
                            "rounds", 4,
                            "max_raw", 4,
                            "max_normalized", 5
                    )
            )
    );

    public static Definition find(String handler) {
        return DEFINITIONS.get(handler);
    }

    public static String unavailableReason(String handler) {
        return switch (handler) {
            case "unscored_rehearsal_v1" ->
                    "Rehearsal is unscored and does not increase "
                            + "the game-points maximum.";
            case "secret_scene_premiere_v1" ->
                    "Premiere guesses and performer bonuses are separate "
                            + "from five-point game contributions.";
            default ->
                    "This game does not yet have a reviewed scoring adapter.";
        };
    }
}