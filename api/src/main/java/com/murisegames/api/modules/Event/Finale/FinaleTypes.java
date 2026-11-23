package com.murisegames.api.modules.Event.Finale;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class FinaleTypes {

    private FinaleTypes() {
    }

    // ---- Host review of guesses (organizer only) ----

    public record ReviewDecisionRequest(
            @NotBlank
            @Pattern(regexp = "correct|wrong", message = "Verdict must be correct or wrong.")
            String verdict
    ) {
    }

    /**
     * One guess as the host sees it.
     * verdict: "correct" | "wrong" | "pending" (pending = needs a host decision before reveal).
     */
    public record ReviewItem(
            UUID guessId,
            UUID guessingTeamId,
            String guessingTeamName,
            UUID targetTeamId,
            String targetTeamName,
            String targetTitle,
            int stage,
            String rawGuess,
            Instant submittedAt,
            boolean autoMatched,
            String hostDecision,
            String verdict,
            boolean sceneRevealed
    ) {
    }

    public record ReviewBoardResponse(
            String guessingStatus,
            int pendingCount,
            List<ReviewItem> items,
            List<UUID> leakedTeamIds
    ) {
    }

    // ---- Leaked title (slide 14 notes) ----

    public record LeakRequest(
            @NotNull UUID teamId
    ) {
    }

    // ---- Title reveals ----

    public record RevealRequest(
            @NotNull UUID requestId,
            @NotNull UUID teamId
    ) {
    }

    public record CorrectGuesserView(
            UUID teamId,
            String teamName,
            String teamColor,
            int stage,
            BigDecimal points
    ) {
    }

    /** Only ever built for scenes that have been revealed. */
    public record RevealView(
            UUID sceneId,
            UUID teamId,
            String teamName,
            String teamColor,
            String title,
            String answerScope,
            Instant revealedAt,
            List<CorrectGuesserView> correctGuessers,
            BigDecimal performerBonus,
            boolean leaked
    ) {
    }

    public record RevealListResponse(
            String guessingStatus,
            List<RevealView> reveals
    ) {
    }
}
