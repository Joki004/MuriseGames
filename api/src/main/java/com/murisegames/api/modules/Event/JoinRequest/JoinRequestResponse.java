package com.murisegames.api.modules.Event.JoinRequest;

import java.time.Instant;
import java.util.UUID;

public record JoinRequestResponse(
        UUID id,
        UUID eventId,
        UUID userId,
        String displayName,
        UUID inviteId,
        String status,
        UUID decidedBy,
        Instant decidedAt,
        Instant createdAt,
        Instant updatedAt
) {

    public static JoinRequestResponse from(JoinRequest request, String displayName) {
        return new JoinRequestResponse(
                request.getId(),
                request.getEventId(),
                request.getUserId(),
                displayName,
                request.getInviteId(),
                request.getStatus(),
                request.getDecidedBy(),
                request.getDecidedAt(),
                request.getCreatedAt(),
                request.getUpdatedAt()
        );
    }
}