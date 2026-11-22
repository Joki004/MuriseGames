package com.murisegames.api.modules.Event.Scoring.Plan;

import com.murisegames.api.modules.Event.Scoring.Adapter.ScorePreviewService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ScoringPlanTypes {

    private ScoringPlanTypes() {
    }

    public record Selection(
            @NotNull UUID scheduleId,
            @NotEmpty @Size(max = 7)
            List<@NotNull UUID> eventGameIds
    ) {
    }

    public record PublishRequest(
            @NotNull UUID requestId,
            @NotNull @Valid Selection selection,
            @NotBlank @Pattern(regexp = "[0-9a-f]{64}")
            String reviewHash,
            @NotEmpty @Size(max = 7)
            List<@NotNull UUID> approvedVersionIds,
            @AssertTrue(message = "Approve Policy A before publishing.")
            boolean approvePolicy,
            @NotBlank @Size(min = 20, max = 2000)
            String decisionNote
    ) {
    }

    public record Draft(UUID id, int version, String mode) {
    }

    public record TeamView(UUID id, String name) {
    }

    public record FixtureView(
            UUID id,
            int round,
            Instant startsAt,
            Instant endsAt,
            List<UUID> teamIds,
            boolean bye
    ) {
    }

    public record Opportunity(
            UUID teamId,
            int appearances,
            int rawDenominator,
            List<UUID> fixtureIds
    ) {
    }

    public record GameView(
            UUID eventGameId,
            UUID gameVersionId,
            String name,
            int ruleVersion,
            String scoringHandler,
            String adapterRevision,
            Map<String, Object> scoringConfig,
            String rulesMarkdown,
            String sourceRef,
            String decisionNote,
            int rawMaximumPerFixture,
            List<FixtureView> fixtures,
            List<Opportunity> opportunities
    ) {
    }

    public record Review(
            UUID eventId,
            UUID scheduleId,
            int scheduleVersion,
            String policy,
            String precision,
            String reviewHash,
            List<TeamView> teams,
            List<GameView> games,
            List<String> excludedGames,
            List<String> blockers,
            int maximumGamePoints,
            int byeCount
    ) {
    }

    public record Published(
            UUID approvedBy,
            Instant publishedAt,
            String decisionNote,
            Map<String, Object> snapshot
    ) {
    }

    public record Setup(
            List<Draft> drafts,
            List<ScorePreviewService.GameOption> games,
            Published published
    ) {
    }
}