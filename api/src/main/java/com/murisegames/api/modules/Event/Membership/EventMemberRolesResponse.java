package com.murisegames.api.modules.Event.Membership;

import java.util.Set;
import java.util.UUID;

public record EventMemberRolesResponse(
        UUID membershipId,
        UUID userId,
        String status,
        Set<String> roles
) {
}