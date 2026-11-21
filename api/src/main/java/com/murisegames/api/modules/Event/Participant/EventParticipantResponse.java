package com.murisegames.api.modules.Event.Participant;

import java.time.Instant;
import java.util.UUID;

public record EventParticipantResponse(
        UUID id,
        UUID eventId,
        UUID userId,
        String displayName,
        UUID teamId,
        String status,
        boolean captain,
        Instant createdAt,
        Instant updatedAt
) {

    public static EventParticipantResponse from(EventParticipant participant) {
        return new EventParticipantResponse(
                participant.getId(),
                participant.getEventId(),
                participant.getUserId(),
                participant.getDisplayName(),
                participant.getTeamId(),
                participant.getStatus(),
                participant.isCaptain(),
                participant.getCreatedAt(),
                participant.getUpdatedAt()
        );
    }
}