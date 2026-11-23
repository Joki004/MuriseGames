package com.murisegames.api.modules.Event.Schedule;

import jakarta.validation.constraints.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ScheduleTypes {

    private ScheduleTypes() {
    }

    public record CreateManualDraftRequest(
            @Size(max = 200) String note
    ) {
    }

    public record GenerateBalancedDraftRequest(
            @NotNull Instant startAt,
            @Min(5) @Max(120) int slotMinutes,
            @Min(0) @Max(60) int breakMinutes,
            Long seed,
            @NotEmpty List<@NotNull UUID> eventGameIds
    ) {
    }

    public record AddFixtureRequest(
            @NotNull UUID eventGameId,
            @Min(1) int roundNumber,
            @NotNull Instant startAt,
            @NotNull Instant endAt,
            @NotEmpty List<@NotNull UUID> teamIds,
            boolean bye,
            UUID resourceId
    ) {
    }

    public record CreateResourceRequest(
            @NotBlank @Pattern(regexp = "room|table|station") String type,
            @NotBlank @Size(max = 100) String name,
            @Min(1) @Max(100) int capacity
    ) {
    }

    public record ResourceView(
            UUID id,
            String type,
            String name,
            int capacity
    ) {
    }

    public record FixtureTeamView(
            UUID teamId,
            String teamName,
            String side
    ) {
    }

    public record FixtureView(
            UUID id,
            UUID slotId,
            UUID eventGameId,
            String gameName,
            int roundNumber,
            String status,
            Instant startAt,
            Instant endAt,
            UUID resourceId,
            String resourceName,
            List<FixtureTeamView> teams
    ) {
    }

    public record ScheduleSummary(
            UUID id,
            int version,
            String mode,
            String status,
            Long seed,
            Instant createdAt
    ) {
    }

    public record ScheduleDetail(
            UUID id,
            UUID eventId,
            int version,
            String mode,
            String status,
            Long seed,
            List<FixtureView> fixtures,
            Map<UUID, Map<UUID, Integer>> opponentMatrix,
            List<String> warnings
    ) {
    }
}