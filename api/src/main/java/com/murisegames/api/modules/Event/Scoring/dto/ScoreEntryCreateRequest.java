package com.murisegames.api.modules.Event.Scoring.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.util.UUID;

public record ScoreEntryCreateRequest(
        @NotNull(message = "teamId is required.")
        UUID teamId,

        @Schema(allowableValues = {"game_points", "bonus", "penalty", "adjustment"})
        @NotBlank
        @Pattern(regexp = "game_points|bonus|penalty|adjustment",
                message = "Category must be game_points, bonus, penalty, or adjustment.")
        String category,

        @NotNull(message = "delta is required.")
        BigDecimal delta,

        @NotBlank(message = "reason is required.")
        String reason,

        @Schema(description = "Optional idempotency key for this score entry.")
        String sourceKey
) {
}