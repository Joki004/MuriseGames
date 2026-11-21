package com.murisegames.api.modules.Event.Live;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record EventSnapshotResponse(
        UUID eventId,
        Instant serverTime,
        long version,
        String eventStatus,
        String scoreboardVisibility,
        Set<String> roles,
        UUID currentEventGameId,
        String phase,
        Instant deadlineAt,
        Long pausedRemainingMs,
        boolean paused,
        Instant stateUpdatedAt
) {
}