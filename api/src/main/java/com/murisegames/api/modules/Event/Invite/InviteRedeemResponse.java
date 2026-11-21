package com.murisegames.api.modules.Event.Invite;

import java.util.UUID;

public record InviteRedeemResponse(
        String status,
        UUID eventId,
        UUID requestId
) {

    public static InviteRedeemResponse joined(UUID eventId) {
        return new InviteRedeemResponse("JOINED", eventId, null);
    }

    public static InviteRedeemResponse pending(UUID requestId) {
        return new InviteRedeemResponse("PENDING", null, requestId);
    }
}