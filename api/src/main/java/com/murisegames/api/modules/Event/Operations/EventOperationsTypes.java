package com.murisegames.api.modules.Event.Operations;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class EventOperationsTypes {

    private EventOperationsTypes() {
    }

    // ---- Offline score sheet sync ----

    public record OfflineScoreRowInput(
            @NotBlank @Size(max = 60) String sheetKey,
            @Min(1) int rowNumber,
            @NotNull UUID teamId,
            @NotBlank
            @Pattern(
                    regexp = "game_points|bonus|penalty|adjustment",
                    message = "Category must be game_points, bonus, penalty, or adjustment."
            )
            String category,
            @NotNull BigDecimal delta,
            @NotBlank @Size(max = 220) String reason
    ) {
    }

    public record OfflineSyncRequest(
            @NotNull UUID batchId,
            @NotEmpty @Size(max = 200) List<@Valid @NotNull OfflineScoreRowInput> rows
    ) {
    }

    /**
     * status: "applied" | "already_synced" | "conflict"
     */
    public record OfflineSyncedRowResult(
            String sheetKey,
            int rowNumber,
            UUID teamId,
            UUID scoreEntryId,
            String status,
            String conflictDetail
    ) {
    }

    public record OfflineSyncResponse(
            UUID batchId,
            int newlyAppliedCount,
            int duplicateSkippedCount,
            int conflictCount,
            List<OfflineSyncedRowResult> results
    ) {
    }

    // ---- Printable packet ----

    public record PrintTeamView(
            UUID id,
            String name,
            String color,
            int sortOrder
    ) {
    }

    public record PrintGameView(
            UUID eventGameId,
            String name,
            String status,
            String houseRulesNote
    ) {
    }

    public record PrintFixtureTeamView(
            String teamName,
            String side
    ) {
    }

    public record PrintFixtureView(
            int roundNumber,
            Instant startAt,
            Instant endAt,
            String gameName,
            String resourceName,
            List<PrintFixtureTeamView> teams
    ) {
    }

    public record PrintClueView(
            UUID teamId,
            String teamName,
            int stage,
            String body
    ) {
    }

    public record PrintSecretTitleView(
            UUID teamId,
            String teamName,
            String title,
            String answerScope,
            List<String> aliases
    ) {
    }

    public record PrintPacketResponse(
            UUID eventId,
            String eventName,
            String eventTheme,
            Instant startsAt,
            String timezone,
            String venue,
            String scope,
            Instant generatedAt,
            List<PrintTeamView> teams,
            List<PrintGameView> games,
            List<PrintFixtureView> fixtures,
            List<PrintClueView> clues,
            List<PrintSecretTitleView> secretTitles,
            List<String> operationalNotes
    ) {
    }
}