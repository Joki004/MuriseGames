package com.murisegames.api.modules.Event.Clue;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ClueReleaseTypes {

    private ClueReleaseTypes() {
    }

    public record StageCommandRequest(
            @NotNull UUID requestId,
            @Min(0) @Max(3) int expectedStage,
            @Min(0) long expectedVersion
    ) {
    }

    public record PublicClue(
            UUID teamId,
            String teamName,
            String teamColor,
            int stage,
            String body
    ) {
    }

    public record ReleaseRecord(
            int stage,
            UUID releasedBy,
            Instant releasedAt
    ) {
    }

    public record ReleasedCluesResponse(
            UUID eventId,
            int currentStage,
            String status,
            long version,
            List<ReleaseRecord> releases,
            List<PublicClue> clues
    ) {
    }
}