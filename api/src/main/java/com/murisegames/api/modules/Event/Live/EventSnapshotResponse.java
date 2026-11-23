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
        List<ActiveFixtureView> activeFixtures,
        // GM-027: Secret Scene guessing state. Screens refetch clues when guessingVersion
        // changes, so a clue release reaches every phone and the TV on the next poll.
        int guessingStage,
        String guessingStatus,
        long guessingVersion
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
                List.of(),
                0,
                "open",
                0L
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