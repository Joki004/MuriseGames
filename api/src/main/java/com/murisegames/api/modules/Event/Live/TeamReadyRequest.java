package com.murisegames.api.modules.Event.Live;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record TeamReadyRequest(
        @NotNull UUID teamId,
        boolean ready
) {
}