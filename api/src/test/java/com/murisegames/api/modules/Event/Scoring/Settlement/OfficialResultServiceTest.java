package com.murisegames.api.modules.Event.Scoring.Settlement;

import com.murisegames.api.modules.Event.Scoring.Adapter.ExactScore;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OfficialResultServiceTest {

    @Test
    void precisionRuleSumsExactBeforeRoundingAndDisplaysOneDecimal() {
        // Hand-check from GM-011: 3/4 * 5 = 3.75 -> displays 3.8
        ExactScore game1 = ExactScore.of(5L * 3, 4);
        ExactScore game2 = ExactScore.of(5L * 3, 4);

        assertThat(game1.display()).isEqualTo("3.8");
        assertThat(game1.settlementAmount()).isEqualByComparingTo("3.7500");

        // Sum before rounding: 3.75 + 3.75 = 7.5 (NOT 3.8 + 3.8 = 7.6)
        ExactScore total = game1.add(game2);
        assertThat(total.display()).isEqualTo("7.5");
        assertThat(total.settlementAmount()).isEqualByComparingTo("7.5000");
    }

    @Test
    void exactTiesShareRank() {
        UUID teamA = UUID.randomUUID();
        UUID teamB = UUID.randomUUID();
        UUID teamC = UUID.randomUUID();

        Map<UUID, String> names = new LinkedHashMap<>();
        names.put(teamA, "Alpha");
        names.put(teamB, "Beta");
        names.put(teamC, "Gamma");

        Map<UUID, ExactScore> totals = Map.of(
                teamA, ExactScore.of(15, 4), // 3.75
                teamB, ExactScore.of(15, 4), // 3.75 (exact tie)
                teamC, ExactScore.of(10, 4)  // 2.50
        );

        List<OfficialResultTypes.RankedTeam> ranked =
                OfficialResultService.rankExactTotals(names, totals);

        assertThat(ranked).hasSize(3);
        assertThat(ranked.get(0).rank()).isEqualTo(1);
        assertThat(ranked.get(1).rank()).isEqualTo(1);
        assertThat(ranked.get(2).rank()).isEqualTo(3);
    }

    @Test
    void correctionReconcilesDeltaOnlyWithoutDuplicatingFullScore() {
        ExactScore initial = ExactScore.of(5L * 3, 4);   // 3.7500
        ExactScore corrected = ExactScore.of(5L * 4, 4); // 5.0000

        BigDecimal delta = corrected.settlementDeltaFrom(initial);
        assertThat(delta).isEqualByComparingTo("1.2500");
        assertThat(initial.settlementAmount().add(delta))
                .isEqualByComparingTo("5.0000");
    }
}