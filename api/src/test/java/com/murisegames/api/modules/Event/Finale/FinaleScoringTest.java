package com.murisegames.api.modules.Event.Finale;

import com.murisegames.api.modules.Event.Finale.FinaleScoring.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FinaleScoringTest {

    private final UUID eventId = UUID.randomUUID();
    private final UUID scene = UUID.randomUUID();
    private final UUID performer = UUID.randomUUID();
    private final UUID teamA = UUID.randomUUID();
    private final UUID teamB = UUID.randomUUID();
    private final UUID teamC = UUID.randomUUID();

    private GuessRecord guess(UUID team, int stage, Verdict verdict) {
        return new GuessRecord(UUID.randomUUID(), team, scene, stage, verdict);
    }

    private SceneSettlement settle(GuessRecord... guesses) {
        return FinaleScoring.settleScene(eventId, scene, performer, List.of(guesses), Rules.DEFAULT);
    }

    private static BigDecimal bd(int v) {
        return BigDecimal.valueOf(v);
    }

    @Test
    void earliestCorrectGuessWinsAndLaterCorrectGuessesAddNothing() {
        var s = settle(
                guess(teamA, 1, Verdict.CORRECT),
                guess(teamA, 2, Verdict.CORRECT),
                guess(teamA, 3, Verdict.CORRECT)
        );

        assertThat(s.totalFor(teamA, Category.GUESS)).isEqualByComparingTo(bd(3));
        assertThat(s.correctGuessers()).singleElement()
                .satisfies(c -> assertThat(c.earliestStage()).isEqualTo(1));
        assertThat(s.totalFor(performer, Category.PERFORMER)).isEqualByComparingTo(bd(1));
    }

    @Test
    void tiersAreFourThreeTwoOneByStage() {
        var s = settle(
                guess(teamA, 0, Verdict.CORRECT),
                guess(teamB, 2, Verdict.CORRECT),
                guess(teamC, 3, Verdict.CORRECT)
        );

        assertThat(s.totalFor(teamA, Category.GUESS)).isEqualByComparingTo(bd(4));
        assertThat(s.totalFor(teamB, Category.GUESS)).isEqualByComparingTo(bd(2));
        assertThat(s.totalFor(teamC, Category.GUESS)).isEqualByComparingTo(bd(1));
    }

    @Test
    void wrongThenCorrectScoresAtTheCorrectStage() {
        var s = settle(
                guess(teamA, 0, Verdict.WRONG),
                guess(teamA, 1, Verdict.BLANK),
                guess(teamA, 3, Verdict.CORRECT)
        );

        assertThat(s.totalFor(teamA, Category.GUESS)).isEqualByComparingTo(bd(1));
    }

    @Test
    void wrongAndBlankGuessesScoreNothing() {
        var s = settle(
                guess(teamA, 0, Verdict.WRONG),
                guess(teamA, 1, Verdict.WRONG),
                guess(teamB, 2, Verdict.BLANK)
        );

        assertThat(s.awards()).isEmpty();
        assertThat(s.correctGuessers()).isEmpty();
    }

    @Test
    void stageZeroCorrectEarnsNoPerformerBonusEvenIfRepeatedLater() {
        var s = settle(
                guess(teamA, 0, Verdict.CORRECT),
                guess(teamA, 2, Verdict.CORRECT)
        );

        assertThat(s.totalFor(teamA, Category.GUESS)).isEqualByComparingTo(bd(4));
        assertThat(s.totalFor(performer, Category.PERFORMER)).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void performerBonusIsOnePerOpposingTeamAndCappedByOpponentCount() {
        var s = settle(
                guess(teamA, 1, Verdict.CORRECT),
                guess(teamA, 2, Verdict.CORRECT),
                guess(teamB, 2, Verdict.CORRECT),
                guess(teamC, 3, Verdict.CORRECT),
                guess(teamC, 3, Verdict.CORRECT) // duplicate row must not double the bonus
        );

        assertThat(s.totalFor(performer, Category.PERFORMER)).isEqualByComparingTo(bd(3));
        assertThat(s.awards().stream().filter(a -> a.category() == Category.PERFORMER)).hasSize(3);
    }

    @Test
    void selfGuessNeverScores() {
        var s = settle(guess(performer, 0, Verdict.CORRECT));

        assertThat(s.awards()).isEmpty();
        assertThat(s.correctGuessers()).isEmpty();
    }

    @Test
    void guessesForOtherScenesAreIgnored() {
        var other = new GuessRecord(UUID.randomUUID(), teamA, UUID.randomUUID(), 0, Verdict.CORRECT);
        var s = FinaleScoring.settleScene(eventId, scene, performer, List.of(other), Rules.DEFAULT);

        assertThat(s.awards()).isEmpty();
    }

    @Test
    void pendingHostReviewBlocksReveal() {
        assertThatThrownBy(() -> settle(
                guess(teamA, 1, Verdict.CORRECT),
                guess(teamB, 2, Verdict.PENDING_REVIEW)
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("host review");
    }

    @Test
    void leakGivesEveryOtherTeamTwoPointsButNotThePerformer() {
        var awards = FinaleScoring.leakAwards(
                eventId, scene, performer, List.of(performer, teamA, teamB, teamC), Rules.DEFAULT);

        assertThat(awards).hasSize(3);
        assertThat(awards).allSatisfy(a -> {
            assertThat(a.category()).isEqualTo(Category.LEAK);
            assertThat(a.points()).isEqualByComparingTo(bd(2));
        });
        assertThat(awards).extracting(Award::teamId)
                .containsExactlyInAnyOrder(teamA, teamB, teamC);
    }

    @Test
    void leakIsAddedOnTopOfGuessingPoints() {
        // Team A guessed before any clue (4), then the title leaked (+2): 6 in total.
        var guessing = settle(guess(teamA, 0, Verdict.CORRECT), guess(teamB, 1, Verdict.CORRECT));
        var leak = FinaleScoring.leakAwards(
                eventId, scene, performer, List.of(performer, teamA, teamB), Rules.DEFAULT);

        BigDecimal teamATotal = guessing.totalFor(teamA, Category.GUESS).add(
                leak.stream().filter(a -> a.teamId().equals(teamA))
                        .map(Award::points).reduce(BigDecimal.ZERO, BigDecimal::add));

        assertThat(teamATotal).isEqualByComparingTo(bd(6));
        // The leak doesn't change the performer bonus: only team B (after clue 1) counts.
        assertThat(guessing.totalFor(performer, Category.PERFORMER)).isEqualByComparingTo(bd(1));
    }

    @Test
    void recordingTheSameLeakTwiceProducesTheSameKeys() {
        var teams = List.of(performer, teamA, teamB);
        var first = FinaleScoring.leakAwards(eventId, scene, performer, teams, Rules.DEFAULT);
        var again = FinaleScoring.leakAwards(eventId, scene, performer, List.of(teamB, teamA, performer), Rules.DEFAULT);

        assertThat(first).isEqualTo(again);
        assertThat(first).extracting(Award::idempotencyKey)
                .allMatch(k -> k.startsWith(FinaleScoring.leakKeyPrefix(eventId, scene)));
    }

    @Test
    void exactTitleOrAliasMatchesIgnoringCaseAccentsAndPunctuation() {
        var aliases = Set.of("lotr", "fellowship of the ring");

        assertThat(FinaleScoring.verdictFor("  The LORD of the rings!! ", "the lord of the rings", aliases, null))
                .isEqualTo(Verdict.CORRECT);
        assertThat(FinaleScoring.verdictFor("L.O.T.R", "the lord of the rings", aliases, null))
                .isEqualTo(Verdict.PENDING_REVIEW); // "l o t r" is not an approved alias
        assertThat(FinaleScoring.verdictFor("lotr", "the lord of the rings", aliases, null))
                .isEqualTo(Verdict.CORRECT);
        assertThat(FinaleScoring.verdictFor("Władca Pierścieni", "wladca pierscieni", Set.of(), null))
                .isEqualTo(Verdict.CORRECT);
    }

    @Test
    void unmatchedGuessWaitsForHostAndHostDecisionAlwaysWins() {
        assertThat(FinaleScoring.verdictFor("the hobbit", "the lord of the rings", Set.of(), null))
                .isEqualTo(Verdict.PENDING_REVIEW);
        assertThat(FinaleScoring.verdictFor("the hobbit", "the lord of the rings", Set.of(), "wrong"))
                .isEqualTo(Verdict.WRONG);
        assertThat(FinaleScoring.verdictFor("LOTR 1", "the lord of the rings", Set.of(), "correct"))
                .isEqualTo(Verdict.CORRECT);
        // Host can also overrule an automatic match.
        assertThat(FinaleScoring.verdictFor("the lord of the rings", "the lord of the rings", Set.of(), "wrong"))
                .isEqualTo(Verdict.WRONG);
    }

    @Test
    void blankGuessIsWrongWithoutReview() {
        assertThat(FinaleScoring.verdictFor(" ?! ", "the lord of the rings", Set.of(), null))
                .isEqualTo(Verdict.BLANK);
    }

    @Test
    void repeatedRevealProducesIdenticalIdempotencyKeys() {
        List<GuessRecord> guesses = List.of(
                guess(teamA, 1, Verdict.CORRECT),
                guess(teamB, 0, Verdict.CORRECT),
                guess(teamC, 3, Verdict.WRONG)
        );

        var first = FinaleScoring.settleScene(eventId, scene, performer, guesses, Rules.DEFAULT);
        var rehearsal = FinaleScoring.settleScene(eventId, scene, performer, new ArrayList<>(guesses), Rules.DEFAULT);

        assertThat(first.awards()).isEqualTo(rehearsal.awards());
        assertThat(first.awards()).extracting(Award::idempotencyKey).doesNotHaveDuplicates();
    }
}
