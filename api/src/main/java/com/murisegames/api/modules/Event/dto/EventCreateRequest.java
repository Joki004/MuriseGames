package com.murisegames.api.modules.Event.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

public record EventCreateRequest(

        @NotBlank
        @Size(max = 160)
        String name,

        @Size(max = 300)
        String theme,

        @NotNull
        OffsetDateTime startsAt,

        @NotBlank
        @Size(max = 80)
        String timezone,

        @Size(max = 300)
        String venue,

        @NotBlank
        @Pattern(
                regexp = "public|private",
                message = "Visibility must be public or private."
        )
        String visibility
) {
}