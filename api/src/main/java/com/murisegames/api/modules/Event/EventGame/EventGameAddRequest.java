package com.murisegames.api.modules.Event.EventGame.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.Map;
import java.util.UUID;

public record EventGameAddRequest(
        @NotNull(message = "gameDefinitionId is required.")
        UUID gameDefinitionId,

        UUID gameVersionId,

        @Min(value = 0, message = "sortOrder must be >= 0.")
        Integer sortOrder,

        String houseRulesNote,

        @Pattern(regexp = "public|hidden",
                message = "scoreboardVisibilityOverride must be public or hidden.")
        String scoreboardVisibilityOverride,

        Map<String, Object> settingsSnapshot
) {
}