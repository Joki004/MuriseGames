package com.murisegames.api.modules.Event.Team.dto;

import jakarta.validation.constraints.*;

public record TeamSaveRequest(
        @NotBlank
        @Size(max = 80)
        String name,

        @NotBlank
        @Pattern(
                regexp = "^#[0-9a-fA-F]{6}$",
                message = "Color must use six-digit hex format, such as #10b981."
        )
        String color,

        @NotNull
        @PositiveOrZero
        Integer sortOrder
) {
}