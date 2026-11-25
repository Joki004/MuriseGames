package com.murisegames.api.modules.Event.PlayerPoints;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Personal points ("Star players"): one point per credit, separate from team scores. */
public final class PlayerPointsTypes {

    private PlayerPointsTypes() {
    }

    /** One credit: this player earned the given segment (Duel 2, Round 1...). */
    public record Credit(int segmentIndex, UUID participantId) {
    }

    public record SaveCreditsRequest(List<Credit> credits) {
    }

    public record FixturePlayer(UUID participantId, UUID userId, String displayName) {
    }

    public record FixtureTeamPlayers(
            UUID teamId,
            String teamName,
            String teamColor,
            List<FixturePlayer> players
    ) {
    }

    public record FixtureCreditsResponse(
            UUID fixtureId,
            String gameName,
            int roundNumber,
            /** Empty when this game has no star players (e.g. the rehearsal). */
            List<String> segments,
            List<FixtureTeamPlayers> teams,
            List<Credit> credits,
            String unavailableReason
    ) {
    }

    public record RankingRow(
            UUID participantId,
            UUID userId,
            String displayName,
            UUID teamId,
            String teamName,
            String teamColor,
            int points,
            int rank
    ) {
    }

    public record RankingResponse(
            UUID eventId,
            String visibility,
            boolean hiddenFromViewer,
            /** Set when the scoreboard is frozen: points after this are not counted yet. */
            Instant frozenAt,
            List<RankingRow> rows
    ) {
    }

    /** A live (not reversed) award in the database. */
    public record LiveAward(UUID id, int segmentIndex, UUID participantId) {
    }
}
