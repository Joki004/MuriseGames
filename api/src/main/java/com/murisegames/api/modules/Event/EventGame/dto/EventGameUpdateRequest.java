package com.murisegames.api.modules.Event.EventGame.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

public record EventGameUpdateRequest(
        @Pattern(regexp = "scheduled|active|settled|voided",
                message = "Status must be scheduled, active, settled, or voided.")
        String status,

        @Min(value = 0, message = "sortOrder must be >= 0.")
        Integer sortOrder,

        String houseRulesNote,

        @Pattern(regexp = "public|hidden",
                message = "scoreboardVisibilityOverride must be public or hidden.")
        String scoreboardVisibilityOverride
) {
}