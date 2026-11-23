package com.murisegames.api.modules.Event.Referee;

import com.murisegames.api.modules.Event.Scoring.Adapter.ScorePreviewService.GameOption;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class RefereeTypes {

    private RefereeTypes() {
    }

    // ---- Organizer: assignments ----

    /** A member holding the REFEREE role. teamName is set when they also play. */
    public record RefereeOption(
            UUID membershipId,
            String displayName,
            UUID teamId,
            String teamName
    ) {
    }

    public record GameView(UUID eventGameId, String name) {
    }

    public record AssignmentView(
            UUID id,
            UUID membershipId,
            UUID eventGameId,
            Instant createdAt
    ) {
    }

    public record RefereeBoardResponse(
            List<RefereeOption> referees,
            List<GameView> games,
            List<AssignmentView> assignments
    ) {
    }

    public record AssignRequest(
            @NotNull UUID membershipId,
            @NotNull UUID eventGameId
    ) {
    }

    // ---- Referee: own desk ----

    public record DeskFixtureTeam(UUID teamId, String teamName, String side) {
    }

    public record DeskFixture(
            UUID fixtureId,
            UUID eventGameId,
            String gameName,
            int roundNumber,
            String status,
            Instant startAt,
            Instant endAt,
            String resourceName,
            List<DeskFixtureTeam> teams,
            /** The referee's own team plays in this match: they may not record it. */
            boolean ownTeam
    ) {
    }

    public record RefereeDeskResponse(
            List<GameOption> games,
            List<DeskFixture> fixtures,
            /** False until the host publishes the scoring plan (and with it the schedule). */
            boolean schedulePublished
    ) {
    }
}
