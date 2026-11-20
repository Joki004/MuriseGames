package com.murisegames.api.modules.Event.Invite;

public record InviteCreatedResponse(
        EventInviteResponse invite,
        String token
) {
}