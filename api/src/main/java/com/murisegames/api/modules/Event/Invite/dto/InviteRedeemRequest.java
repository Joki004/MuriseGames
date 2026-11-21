package com.murisegames.api.modules.Event.Invite.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record InviteRedeemRequest(
        @NotBlank
        @Pattern(
                regexp = "^[A-Za-z0-9_-]{43}$",
                message = "Invalid invitation."
        )
        String token
) {
}