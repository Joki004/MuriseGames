package com.murisegames.api.modules.Event.Invite;

import java.time.Instant;
import java.util.UUID;

public record EventInviteResponse(
        UUID id,
        UUID eventId,
        Instant expiresAt,
        Instant revokedAt,
        Integer maxUses,
        int useCount,
        Instant createdAt,
        String status
) {

    public static EventInviteResponse from(EventInvite invite) {
        String status;

        if (invite.getRevokedAt() != null) {
            status = "REVOKED";
        } else if (!invite.getExpiresAt().isAfter(Instant.now())) {
            status = "EXPIRED";
        } else if (invite.getMaxUses() != null
                && invite.getUseCount() >= invite.getMaxUses()) {
            status = "EXHAUSTED";
        } else {
            status = "ACTIVE";
        }

        return new EventInviteResponse(
                invite.getId(),
                invite.getEventId(),
                invite.getExpiresAt(),
                invite.getRevokedAt(),
                invite.getMaxUses(),
                invite.getUseCount(),
                invite.getCreatedAt(),
                status
        );
    }
}