package com.murisegames.api.modules.Event.Scoring.TeamResults;

import com.murisegames.api.modules.Event.Scoring.ScoreboardRowResponse;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class TeamResultsTypes {

    private TeamResultsTypes() {
    }

    public static final String OWN_RESULTS = "own_results";
    public static final String OWN_POINTS = "own_points";
    public static final String FULL_SCOREBOARD = "full_scoreboard";

    public record SettingRequest(
            @NotBlank
            @Pattern(
                    regexp = "own_results|own_points|full_scoreboard",
                    message = "Mode must be own_results, own_points or full_scoreboard."
            )
            String mode
    ) {
    }

    public record SettingResponse(String mode) {
    }

    public record OpponentView(
            UUID teamId,
            String teamName,
            BigDecimal rawScore,
            List<Integer> values,
            String outcome
    ) {
    }

    /**
     * One match of the viewer's team. status: completed | voided | scheduled | active.
     * Result fields are null until a result is recorded.
     */
    public record MatchView(
            UUID fixtureId,
            String gameName,
            int roundNumber,
            Instant startAt,
            String status,
            String outcome,
            BigDecimal rawScore,
            BigDecimal rawMax,
            List<String> fieldLabels,
            List<Integer> values,
            List<OpponentView> opponents,
            boolean corrected,
            Instant recordedAt
    ) {
    }

    public record GamePoints(UUID eventGameId, String gameName, BigDecimal points) {
    }

    /**
     * Fields beyond {@code matches} are null when the host's mode doesn't allow them:
     * points need own_points or full_scoreboard; scoreboard needs full_scoreboard.
     */
    public record MyResultsResponse(
            String mode,
            UUID teamId,
            String teamName,
            String teamColor,
            List<MatchView> matches,
            List<GamePoints> gamePoints,
            BigDecimal otherPoints,
            BigDecimal totalPoints,
            List<ScoreboardRowResponse> scoreboard
    ) {
    }
}
