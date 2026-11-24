package com.murisegames.api.modules.Event.Alibi.Game;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * GM-046 shapes. Each role has its own view and never receives fields it may not see:
 * the TV never gets fixed facts, equivalents or unrevealed answers; a player never
 * gets the partner's answer or the judge's material.
 */
public final class AlibiGameTypes {

    private AlibiGameTypes() {
    }

    // ---- requests -------------------------------------------------------------------

    public record CreateSessionRequest(@NotNull UUID fixtureId) {
    }

    /** Pick the pair, and either a case or random = true. */
    public record SetupRequest(
            @NotNull UUID playerA,
            @NotNull UUID playerB,
            UUID caseId,
            boolean random
    ) {
    }

    public record AnswerRequest(
            @Size(max = AlibiGameRules.MAX_ANSWER_LENGTH) String text,
            @Min(0) Integer awayMs,
            @Min(0) Integer awayCount
    ) {
    }

    public record VerdictRequest(
            @NotNull Boolean match,
            /* Required to change a verdict that is already on the TV. */
            @Size(max = 300) String reason
    ) {
    }

    // ---- shared pieces ----------------------------------------------------------------

    public record Member(UUID userId, String name) {
    }

    public record CaseOption(UUID caseId, String label) {
    }

    public record RevealedItem(
            int number,
            String question,
            String answerA,
            String answerB,
            boolean match
    ) {
    }

    // ---- host (organizers) -------------------------------------------------------------

    public record HostView(
            Instant serverTime,
            boolean planPublished,
            List<TeamSlot> teams
    ) {
    }

    public record TeamSlot(
            UUID fixtureId,
            UUID eventGameId,
            UUID teamId,
            String teamName,
            String teamColor,
            String fixtureStatus,
            UUID sessionId,
            List<Member> members,
            /* Cases this team may be given: ready, unused, unknown to its members. */
            List<CaseOption> eligibleCases,
            List<RoundHost> rounds,
            int totalMatches,
            boolean recorded,
            boolean needsRecording
    ) {
    }

    /** Progress only: the host screen never shows the case content. */
    public record RoundHost(
            UUID roundId,
            int pairNumber,
            String status,
            UUID playerA,
            String playerAName,
            UUID playerB,
            String playerBName,
            UUID caseId,
            String caseLabel,
            Instant prepEndsAt,
            int currentQuestion,
            int questionCount,
            Instant questionClosesAt,
            boolean lockedA,
            boolean lockedB,
            int comparedCount,
            int decidedCount,
            int revealedCount,
            int matches
    ) {
    }

    // ---- judge (organizers and assigned referees, never their own team) -----------------

    public record JudgeView(Instant serverTime, List<JudgeRound> rounds) {
    }

    public record JudgeRound(
            UUID roundId,
            String teamName,
            int pairNumber,
            String status,
            String caseLabel,
            String playerAName,
            String playerBName,
            String brief,
            List<String> fixedFacts,
            String judgeRubric,
            int questionCount,
            int currentQuestion,
            /* Only questions where both answers are locked; the other side stays hidden until then. */
            List<JudgePrompt> prompts
    ) {
    }

    public record JudgePrompt(
            UUID promptId,
            int number,
            String question,
            List<String> validEquivalents,
            String judgeNote,
            AnswerView a,
            AnswerView b,
            Boolean verdict,
            String verdictSource,
            boolean revealed
    ) {
    }

    public record AnswerView(String text, boolean missing, int awayMs, int awayCount) {
    }

    // ---- player (one side of a pair) --------------------------------------------------

    public record PlayerView(Instant serverTime, PlayerRound round) {
    }

    public record PlayerRound(
            UUID roundId,
            String teamName,
            int pairNumber,
            String side,
            String partnerName,
            String status,
            Instant prepEndsAt,
            /* Only while preparing. */
            String brief,
            List<String> fixedFacts,
            /* Only while questioning. */
            PlayerQuestion question,
            int questionCount,
            List<RevealedItem> revealed,
            int matches
    ) {
    }

    public record PlayerQuestion(
            UUID promptId,
            int number,
            String text,
            Instant closesAt,
            boolean locked,
            boolean missing,
            String myAnswer
    ) {
    }

    // ---- TV / audience -----------------------------------------------------------------

    public record PublicView(Instant serverTime, PublicRound round) {
    }

    public record PublicRound(
            String teamName,
            String teamColor,
            int pairNumber,
            String playerAName,
            String playerBName,
            String status,
            Instant prepEndsAt,
            String brief,
            int currentQuestion,
            int questionCount,
            String questionText,
            Instant questionClosesAt,
            boolean lockedA,
            boolean lockedB,
            List<RevealedItem> revealed,
            int matches
    ) {
    }
}
