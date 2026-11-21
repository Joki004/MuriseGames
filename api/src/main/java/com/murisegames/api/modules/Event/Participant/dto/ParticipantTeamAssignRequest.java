package com.murisegames.api.modules.Event.Participant.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

public record ParticipantTeamAssignRequest(
        @Schema(description = "Target team ID, or null to unassign the player from their current team.")
        UUID teamId
) {
}