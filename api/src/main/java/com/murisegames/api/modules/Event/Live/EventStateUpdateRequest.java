package com.murisegames.api.modules.Event.Live;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record EventStateUpdateRequest(
        UUID currentEventGameId,
        @NotBlank String phase,
        @Min(0) Integer durationSeconds
) {
}