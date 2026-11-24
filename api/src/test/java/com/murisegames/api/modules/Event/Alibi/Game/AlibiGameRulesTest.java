package com.murisegames.api.modules.Event.Alibi.Game;

import com.murisegames.api.modules.Event.Alibi.Game.AlibiGameRules.CaseCandidate;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AlibiGameRulesTest {

    @Test
    void normalizeIgnoresCaseAccentsPunctuationAndSpaces() {
        assertThat(AlibiGameRules.normalize("  Pizza,   Margherita! ")).isEqualTo("pizza margherita");
        assertThat(AlibiGameRules.normalize("Crème brûlée")).isEqualTo("creme brulee");
        assertThat(AlibiGameRules.normalize(null)).isEmpty();
    }

    @Test
    void aBlankOrMissingAnswerOnEitherSideScoresZero() {
        assertThat(AlibiGameRules.autoVerdict("", "pizza", List.of())).isFalse();
        assertThat(AlibiGameRules.autoVerdict("pizza", null, List.of())).isFalse();
    }

    @Test
    void twoBlanksNeverMatch() {
        assertThat(AlibiGameRules.autoVerdict(" ", null, List.of())).isFalse();
        assertThat(AlibiGameRules.autoVerdict("...", "", List.of())).isFalse();
    }

    @Test
    void identicalAnswersMatchAutomatically() {
        assertThat(AlibiGameRules.autoVerdict("Red wine", "red WINE.", List.of())).isTrue();
    }

    @Test
    void differentWordingIsLeftToTheJudge() {
        assertThat(AlibiGameRules.autoVerdict("red wine", "a glass of merlot", List.of())).isNull();
    }

    @Test
    void fixedFactQuestionMatchesWhenBothGiveAnAcceptedAnswer() {
        List<String> accepted = List.of("20:00", "8pm", "eight");
        assertThat(AlibiGameRules.autoVerdict("8pm", "Eight", accepted)).isTrue();
    }

    @Test
    void fixedFactQuestionWithAnUnlistedAnswerGoesToTheJudge() {
        List<String> accepted = List.of("20:00", "8pm");
        // Same words on both sides, but not an accepted answer: may contradict the fact.
        assertThat(AlibiGameRules.autoVerdict("9pm", "9pm", accepted)).isNull();
    }

    @Test
    void aQuestionIsOverWhenBothLockedOrAfterTheGrace() {
        Instant closes = Instant.parse("2026-11-28T20:00:30Z");
        assertThat(AlibiGameRules.questionOver(2, closes, closes.minusSeconds(20))).isTrue();
        assertThat(AlibiGameRules.questionOver(1, closes, closes.plusSeconds(1))).isFalse();
        assertThat(AlibiGameRules.questionOver(1, closes, closes.plus(AlibiGameRules.GRACE))).isTrue();
    }

    @Test
    void scoreCountsOnlyMatches() {
        assertThat(AlibiGameRules.score(Arrays.asList(true, false, null, true, true))).isEqualTo(3);
    }

    @Test
    void aTeamOnlyGetsReadyUnusedCasesNoneOfItsMembersKnows() {
        UUID me = UUID.randomUUID();
        UUID teammate = UUID.randomUUID();
        UUID outsider = UUID.randomUUID();

        CaseCandidate fresh = new CaseCandidate(UUID.randomUUID(), "Fresh", true, Set.of(outsider));
        CaseCandidate knownByTeammate = new CaseCandidate(UUID.randomUUID(), "Known", true, Set.of(teammate));
        CaseCandidate draft = new CaseCandidate(UUID.randomUUID(), "Draft", false, Set.of());
        CaseCandidate played = new CaseCandidate(UUID.randomUUID(), "Played", true, Set.of());

        List<CaseCandidate> eligible = AlibiGameRules.eligible(
                List.of(fresh, knownByTeammate, draft, played),
                Set.of(played.caseId()),
                Set.of(me, teammate)
        );

        assertThat(eligible).containsExactly(fresh);
    }
}
