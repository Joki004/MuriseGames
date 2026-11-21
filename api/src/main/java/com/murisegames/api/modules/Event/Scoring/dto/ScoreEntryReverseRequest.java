package com.murisegames.api.modules.Event.Scoring.dto;

import jakarta.validation.constraints.NotBlank;

public record ScoreEntryReverseRequest(
        @NotBlank(message = "Reversal reason is required.")
        String reason
) {
}