package com.murisegames.api.modules.Event.Scoring.Settlement;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class OfficialResultTypes {

    private OfficialResultTypes() {
    }

    public record RecordFixtureResultRequest(
            @NotEmpty Map<UUID, List<Integer>> teamValues,
            @NotBlank @Size(min = 3, max = 500) String reason
    ) {
    }

    public record CancelFixtureRequest(
            @NotBlank @Size(min = 5, max = 500) String reason
    ) {
    }

    public record TeamSettlement(
            UUID teamId,
            int fixtureRawScore,
            int cumulativeRawScore,
            int frozenDenominator,
            BigDecimal settledTotal,
            BigDecimal deltaPosted,
            String displayTotal,
            int revision
    ) {
    }

    public record FixtureSettlementResponse(
            UUID eventId,
            UUID eventGameId,
            UUID fixtureId,
            String status,
            List<TeamSettlement> teams
    ) {
    }

    public record RankedTeam(
            int rank,
            UUID teamId,
            String teamName,
            String exactNumerator,
            String exactDenominator,
            String displayPoints,
            BigDecimal settledPoints
    ) {
    }

    public record RankingStatusResponse(
            UUID eventId,
            String policy,
            int maximumGamePoints,
            boolean finalRankingAllowed,
            List<String> blockers,
            List<String> includedGames,
            List<RankedTeam> standings
    ) {
    }
}