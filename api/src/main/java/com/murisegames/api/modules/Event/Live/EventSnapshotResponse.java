package com.murisegames.api.modules.Event.Live;

import java.time.Instant;
import java.util.List;
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
        Instant currentWindowStartAt,
        String phase,
        Instant deadlineAt,
        Long pausedRemainingMs,
        boolean paused,
        Instant stateUpdatedAt,
        Set<UUID> readyTeamIds,
        List<ActiveFixtureView> activeFixtures
) {

    public EventSnapshotResponse(
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
            Instant stateUpdatedAt,
            Set<UUID> readyTeamIds
    ) {
        this(
                eventId,
                serverTime,
                version,
                eventStatus,
                scoreboardVisibility,
                roles,
                currentEventGameId,
                null,
                phase,
                deadlineAt,
                pausedRemainingMs,
                paused,
                stateUpdatedAt,
                readyTeamIds,
                List.of()
        );
    }

    public record ActiveFixtureTeamView(
            UUID teamId,
            String teamName,
            String teamColor,
            String side
    ) {
    }

    public record ActiveFixtureView(
            UUID fixtureId,
            UUID eventGameId,
            String gameName,
            String gameCode,
            int roundNumber,
            String status,
            Instant startAt,
            Instant endAt,
            String resourceName,
            List<ActiveFixtureTeamView> teams
    ) {
    }
}