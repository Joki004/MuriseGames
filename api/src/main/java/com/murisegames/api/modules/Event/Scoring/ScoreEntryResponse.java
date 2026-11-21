package com.murisegames.api.modules.Event.Scoring;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ScoreEntryResponse(
        UUID id,
        UUID eventId,
        UUID teamId,
        String teamName,
        UUID resultId,
        String category,
        BigDecimal delta,
        String sourceKey,
        String reason,
        UUID actorId,
        UUID reversesEntryId,
        boolean reversed,
        Instant createdAt
) {
}