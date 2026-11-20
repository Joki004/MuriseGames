package com.murisegames.api.modules.Event.Invite.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record InviteCreateRequest(
        @NotNull
        @Min(1)
        @Max(720)
        Integer expiresInHours,

        @Min(1)
        Integer maxUses
) {
}