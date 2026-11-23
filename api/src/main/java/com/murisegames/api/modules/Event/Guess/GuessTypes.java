package com.murisegames.api.modules.Event.Guess;

import jakarta.validation.constraints.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class GuessTypes {

    private GuessTypes() {
    }

    public record SubmitGuessRequest(
            @NotNull UUID requestId,
            @NotNull UUID targetTeamId,
            @Min(0) @Max(3) int expectedStage,
            @Min(0) long expectedVersion,
            @NotBlank @Size(max = 160, message = "Guess must be 160 characters or fewer.") String guess
    ) {
    }

    public record GuessReceipt(
            UUID id,
            UUID targetTeamId,
            String targetTeamName,
            int stage,
            String rawGuess,
            Instant submittedAt,
            String receiptStatus
    ) {
    }

    public record OpposingTarget(
            UUID teamId,
            String teamName,
            String teamColor,
            boolean hasRegisteredScene
    ) {
    }

    public record CaptainGuessBoardResponse(
            UUID guessingTeamId,
            String guessingTeamName,
            boolean captain,
            int currentStage,
            String guessingStatus,
            long version,
            List<OpposingTarget> targets,
            List<GuessReceipt> submittedGuesses
    ) {
    }
}