package com.murisegames.api.modules.Event.JoinRequest.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

public record JoinRequestApproveRequest(
        @Schema(description = "Optional team ID to assign immediately upon approval.")
        UUID teamId
) {
}