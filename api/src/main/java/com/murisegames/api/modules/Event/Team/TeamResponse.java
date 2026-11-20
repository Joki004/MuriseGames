package com.murisegames.api.modules.Event.Team;

import java.time.Instant;
import java.util.UUID;

public record TeamResponse(
        UUID id,
        UUID eventId,
        String name,
        String color,
        int sortOrder,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {

    public static TeamResponse from(Team team) {
        return new TeamResponse(
                team.getId(),
                team.getEventId(),
                team.getName(),
                team.getColor(),
                team.getSortOrder(),
                team.isActive(),
                team.getCreatedAt(),
                team.getUpdatedAt()
        );
    }
}