package com.murisegames.api.modules.User;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String displayName,
        List<PlatformRole> platformRoles,
        Instant createdAt,
        Instant updatedAt
) {

    public static UserResponse from(User user, List<PlatformRole> platformRoles) {
        return new UserResponse(
                user.getId(),
                user.getDisplayName(),
                platformRoles == null ? List.of() : List.copyOf(platformRoles),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }
}