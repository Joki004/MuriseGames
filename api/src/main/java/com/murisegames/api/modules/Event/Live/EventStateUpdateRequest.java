package com.murisegames.api.modules.Event.Live;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.UUID;

public record EventStateUpdateRequest(
        UUID currentEventGameId,
        Instant windowStartAt,
        @NotBlank String phase,
        @Min(0) Integer durationSeconds
) {
    public EventStateUpdateRequest(
            UUID currentEventGameId,
            String phase,
            Integer durationSeconds
    ) {
        this(currentEventGameId, null, phase, durationSeconds);
    }
}