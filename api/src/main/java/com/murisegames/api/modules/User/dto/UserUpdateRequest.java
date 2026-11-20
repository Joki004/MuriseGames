package com.murisegames.api.modules.User.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UserUpdateRequest(
        @Schema(description = "Public display name shown in events and rosters.", example = "Joram")
        @NotBlank(message = "Display name is required.")
        @Size(max = 80, message = "Display name must be at most 80 characters.")
        String displayName
) {
}