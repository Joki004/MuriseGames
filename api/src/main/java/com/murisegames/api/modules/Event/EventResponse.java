package com.murisegames.api.modules.Event;

import java.time.Instant;
import java.util.UUID;

public record EventResponse(
        UUID id,
        String name,
        String theme,
        Instant startsAt,
        String timezone,
        String venue,
        String status,
        String visibility,
        String scoreboardVisibility,
        UUID createdBy,
        Instant createdAt,
        Instant updatedAt
) {

    public static EventResponse from(Event event) {
        return new EventResponse(
                event.getId(),
                event.getName(),
                event.getTheme(),
                event.getStartsAt(),
                event.getTimezone(),
                event.getVenue(),
                event.getStatus(),
                event.getVisibility(),
                event.getScoreboardVisibility(),
                event.getCreatedBy(),
                event.getCreatedAt(),
                event.getUpdatedAt()
        );
    }
}