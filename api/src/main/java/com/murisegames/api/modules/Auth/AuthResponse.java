package com.murisegames.api.modules.Auth;

import com.murisegames.api.modules.User.PlatformRole;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AuthResponse(
        UUID id,
        String email,
        String displayName,
        List<PlatformRole> platformRoles,
        Instant createdAt,
        Instant updatedAt
) {
}