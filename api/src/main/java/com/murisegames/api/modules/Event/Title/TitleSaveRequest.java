package com.murisegames.api.modules.Event.Title;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record TitleSaveRequest(
        @NotBlank
        @Size(max = 120, message = "A title can have at most 120 characters.")
        String title,

        @NotBlank
        @Pattern(
                regexp = "^(film|show|stage_play|franchise)$",
                message = "Choose film, show, stage_play or franchise."
        )
        String answerScope,

        @NotNull
        @Size(max = 10, message = "At most 10 aliases.")
        List<@Size(max = 120, message = "An alias can have at most 120 characters.") String> aliases
) {
}