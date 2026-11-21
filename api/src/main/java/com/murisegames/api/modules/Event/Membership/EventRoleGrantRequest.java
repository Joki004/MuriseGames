package com.murisegames.api.modules.Event.Membership;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record EventRoleGrantRequest(
        @NotNull UUID userId,
        @NotBlank String role
) {
}