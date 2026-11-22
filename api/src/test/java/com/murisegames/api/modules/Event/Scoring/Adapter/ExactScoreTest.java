package com.murisegames.api.modules.Event.Scoring.Adapter;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class ExactScoreTest {

    @Test
    void cupExampleDisplaysThreePointEight() {
        ExactScore score = ExactScore.of(3 * 5, 4);

        assertEquals("3.8", score.display());
        assertEquals(new BigDecimal("3.7500"), score.settlementAmount());
    }

    @Test
    void sumsExactValuesBeforeDisplayRounding() {
        ExactScore oneGame = ExactScore.of(1, 3);

        assertEquals("0.3", oneGame.display());
        assertEquals(
                "1.0",
                oneGame.add(oneGame).add(oneGame).display()
        );
    }

    @Test
    void equalDisplayedScoresDoNotCreateAnExactTie() {
        ExactScore lower = ExactScore.of(374, 100);
        ExactScore higher = ExactScore.of(3739, 1000);

        assertEquals("3.7", lower.display());
        assertEquals("3.7", higher.display());

        // 3.740 is greater than 3.739 despite matching display text.
        assertTrue(lower.compareTo(higher) > 0);
    }

    @Test
    void equivalentFractionsTieExactly() {
        assertEquals(
                0,
                ExactScore.of(1, 3).compareTo(ExactScore.of(2, 6))
        );
    }

    @Test
    void correctionsReconcileCumulativeSettlementAmounts() {
        ExactScore previous = ExactScore.of(1, 3);
        ExactScore corrected = ExactScore.of(2, 3);

        BigDecimal delta = corrected.settlementDeltaFrom(previous);

        assertEquals(new BigDecimal("0.3334"), delta);
        assertEquals(
                corrected.settlementAmount(),
                previous.settlementAmount().add(delta)
        );
    }
}