package com.murisegames.api.modules.Event.Scoring;

import java.math.BigDecimal;
import java.util.UUID;

public record ScoreboardRowResponse(
        UUID teamId,
        String teamName,
        String teamColor,
        BigDecimal points,
        int rank
) {
}