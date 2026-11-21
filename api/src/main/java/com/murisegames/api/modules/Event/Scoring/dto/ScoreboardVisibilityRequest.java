package com.murisegames.api.modules.Event.Scoring.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ScoreboardVisibilityRequest(
        @Schema(allowableValues = {"public", "hidden", "frozen"})
        @NotBlank
        @Pattern(regexp = "public|hidden|frozen",
                message = "Visibility must be public, hidden, or frozen.")
        String visibility
) {
}