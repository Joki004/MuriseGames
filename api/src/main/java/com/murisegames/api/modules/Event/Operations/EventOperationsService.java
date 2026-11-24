package com.murisegames.api.modules.Event.Operations;

import com.murisegames.api.modules.Audit.AuditService;
import com.murisegames.api.modules.Catalog.GameDefinition;
import com.murisegames.api.modules.Catalog.GameDefinitionRepository;
import com.murisegames.api.modules.Event.Clue.ClueBoardResponse;
import com.murisegames.api.modules.Event.Clue.ClueReleaseService;
import com.murisegames.api.modules.Event.Clue.ClueReleaseTypes.ReleasedCluesResponse;
import com.murisegames.api.modules.Event.Clue.ClueService;
import com.murisegames.api.modules.Event.Event;
import com.murisegames.api.modules.Event.EventGame.EventGame;
import com.murisegames.api.modules.Event.EventGame.EventGameRepository;
import com.murisegames.api.modules.Event.EventRepository;
import com.murisegames.api.modules.Event.Membership.EventMembershipService;
import com.murisegames.api.modules.Event.Schedule.ScheduleService;
import com.murisegames.api.modules.Event.Schedule.ScheduleTypes.ScheduleDetail;
import com.murisegames.api.modules.Event.Schedule.ScheduleTypes.ScheduleSummary;
import com.murisegames.api.modules.Event.Scoring.ScoreEntryResponse;
import com.murisegames.api.modules.Event.Scoring.ScoringService;
import com.murisegames.api.modules.Event.Scoring.dto.ScoreEntryCreateRequest;
import com.murisegames.api.modules.Event.Team.TeamRepository;
import com.murisegames.api.modules.Event.Title.TitleBoardResponse;
import com.murisegames.api.modules.Event.Title.TitleService;
import com.murisegames.api.modules.User.UserService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.murisegames.api.modules.Event.Operations.EventOperationsTypes.*;

@Service
@RequiredArgsConstructor
public class EventOperationsService {

    private static final int MAX_LEDGER_REASON_LEN = 300;

    private final EventRepository eventRepository;
    private final EventMembershipService membershipService;
    private final UserService userService;
    private final ScoringService scoringService;
    private final TeamRepository teamRepository;
    private final EventGameRepository eventGameRepository;
    private final GameDefinitionRepository gameDefinitionRepository;
    private final ScheduleService scheduleService;
    private final ClueReleaseService clueReleaseService;
    private final ClueService clueService;
    private final TitleService titleService;
    private final AuditService auditService;
    private final EntityManager entityManager;

    @Transactional
    public OfflineSyncResponse syncOfflineRows(
            Jwt jwt,
            UUID eventId,
            OfflineSyncRequest request
    ) {
        UUID actorId = userService.extractUserId(jwt);
        membershipService.requireOrganizer(eventId, actorId);

        eventRepository.lockById(eventId)
                .orElseThrow(() -> new EntityNotFoundException("Event not found."));
        membershipService.requireOrganizer(eventId, actorId);

        // Reject duplicate (normalizedSheetKey, rowNumber) within the same request payload.
        Set<String> seenInPayload = new HashSet<>();
        for (OfflineScoreRowInput row : request.rows()) {
            String key = normalizeSheetKey(row.sheetKey()) + "#" + row.rowNumber();
            if (!seenInPayload.add(key)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Duplicate sheet/row in request: " + row.sheetKey().trim() + " #" + row.rowNumber()
                );
            }
        }

        int applied = 0;
        int skipped = 0;
        int conflicts = 0;
        List<OfflineSyncedRowResult> results = new ArrayList<>();

        for (OfflineScoreRowInput row : request.rows()) {
            String normalizedSheetKey = normalizeSheetKey(row.sheetKey());
            String cleanCategory = row.category().trim();
            String cleanReason = row.reason().trim();

            ExistingSyncedRow existing = findExistingRow(eventId, normalizedSheetKey, row.rowNumber());
            if (existing != null) {
                String mismatch = describeMismatch(existing, row.teamId(), cleanCategory, row.delta(), cleanReason);
                if (mismatch != null) {
                    conflicts++;
                    results.add(new OfflineSyncedRowResult(
                            normalizedSheetKey,
                            row.rowNumber(),
                            row.teamId(),
                            existing.scoreEntryId(),
                            "conflict",
                            mismatch
                    ));
                } else {
                    skipped++;
                    results.add(new OfflineSyncedRowResult(
                            normalizedSheetKey,
                            row.rowNumber(),
                            row.teamId(),
                            existing.scoreEntryId(),
                            "already_synced",
                            null
                    ));
                }
                continue;
            }

            String sourceKey = "offline:" + normalizedSheetKey + ":row:" + row.rowNumber();
            String ledgerReason = formatLedgerReason(normalizedSheetKey, row.rowNumber(), cleanReason);

            ScoreEntryResponse entry = scoringService.addEntry(
                    jwt,
                    eventId,
                    new ScoreEntryCreateRequest(
                            row.teamId(),
                            cleanCategory,
                            row.delta(),
                            ledgerReason,
                            sourceKey
                    )
            );

            entityManager.createNativeQuery("""
                    INSERT INTO murise.offline_score_rows (
                        event_id, batch_id, sheet_key, row_number, team_id,
                        category, delta, reason, score_entry_id, synced_by
                    )
                    VALUES (
                        :eventId, :batchId, :sheetKey, :rowNumber, :teamId,
                        :category, :delta, :reason, :scoreEntryId, :syncedBy
                    )
                    """)
                    .setParameter("eventId", eventId)
                    .setParameter("batchId", request.batchId())
                    .setParameter("sheetKey", normalizedSheetKey)
                    .setParameter("rowNumber", row.rowNumber())
                    .setParameter("teamId", row.teamId())
                    .setParameter("category", cleanCategory)
                    .setParameter("delta", row.delta())
                    .setParameter("reason", cleanReason)
                    .setParameter("scoreEntryId", entry.id())
                    .setParameter("syncedBy", actorId)
                    .executeUpdate();

            applied++;
            results.add(new OfflineSyncedRowResult(
                    normalizedSheetKey,
                    row.rowNumber(),
                    row.teamId(),
                    entry.id(),
                    "applied",
                    null
            ));
        }

        auditService.record(
                eventId,
                actorId,
                "offline_scores.synced",
                "offline_score_batch",
                request.batchId(),
                Map.of(
                        "appliedCount", applied,
                        "skippedCount", skipped,
                        "conflictCount", conflicts,
                        "totalRows", request.rows().size()
                )
        );

        return new OfflineSyncResponse(request.batchId(), applied, skipped, conflicts, results);
    }

    @Transactional(readOnly = true)
    public PrintPacketResponse buildPrintPacket(
            Jwt jwt,
            UUID eventId,
            String requestedScope,
            boolean includeSecrets
    ) {
        UUID actorId = userService.extractUserId(jwt);
        Set<String> roles = membershipService.requireAccess(eventId, actorId);
        boolean isOrganizer = roles.contains("ORGANIZER");

        String effectiveScope = (isOrganizer && "host".equalsIgnoreCase(requestedScope))
                ? "host"
                : "public";
        boolean allowSecrets = isOrganizer && "host".equals(effectiveScope) && includeSecrets;

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EntityNotFoundException("Event not found."));

        List<PrintTeamView> teams = teamRepository
                .findByEventIdAndActiveTrueOrderBySortOrderAscNameAsc(eventId)
                .stream()
                .map(t -> new PrintTeamView(t.getId(), t.getName(), t.getColor(), t.getSortOrder()))
                .toList();

        List<EventGame> eventGames = eventGameRepository
                .findByEventIdOrderBySortOrderAscCreatedAtAsc(eventId);
        Map<UUID, GameDefinition> definitionsById = gameDefinitionRepository
                .findAllById(eventGames.stream().map(EventGame::getGameDefinitionId).toList())
                .stream()
                .collect(Collectors.toMap(GameDefinition::getId, Function.identity()));

        List<PrintGameView> games = eventGames.stream()
                .map(eg -> {
                    GameDefinition def = definitionsById.get(eg.getGameDefinitionId());
                    return new PrintGameView(
                            eg.getId(),
                            def != null ? def.getName() : "Game",
                            eg.getStatus(),
                            eg.getHouseRulesNote()
                    );
                })
                .toList();

        List<PrintFixtureView> fixtures = loadPrintableFixtures(jwt, eventId);

        // Host binder gets all approved clues (stages 1..3) so the host can run the event offline.
        // Public/player packet gets ONLY already-released clues via ClueReleaseService.
        List<PrintClueView> clues;
        if ("host".equals(effectiveScope)) {
            ClueBoardResponse board = clueService.board(jwt, eventId);
            clues = board.teams().stream()
                    .flatMap(team -> team.clues().stream()
                            .filter(ClueBoardResponse.Clue::approved)
                            .map(c -> new PrintClueView(
                                    team.teamId(),
                                    team.teamName(),
                                    c.stage(),
                                    c.body()
                            )))
                    .sorted(Comparator
                            .comparingInt(PrintClueView::stage)
                            .thenComparing(PrintClueView::teamName, String.CASE_INSENSITIVE_ORDER))
                    .toList();
        } else {
            ReleasedCluesResponse released = clueReleaseService.getReleasedClues(jwt, eventId);
            clues = released.clues().stream()
                    .map(c -> new PrintClueView(
                            c.teamId(),
                            c.teamName(),
                            c.stage(),
                            c.body()
                    ))
                    .toList();
        }

        List<PrintSecretTitleView> secretTitles = List.of();
        if (allowSecrets) {
            TitleBoardResponse titleBoard = titleService.board(jwt, eventId);
            secretTitles = titleBoard.teams().stream()
                    .filter(t -> t.title() != null && !t.title().isBlank())
                    .map(t -> new PrintSecretTitleView(
                            t.teamId(),
                            t.teamName(),
                            t.title(),
                            t.answerScope(),
                            t.aliases()
                    ))
                    .toList();
        }

        List<String> notes = new ArrayList<>();
        notes.add("Generated in " + effectiveScope.toUpperCase(Locale.ROOT) + " mode.");
        notes.add("Alibi & Social Case cards are managed outside the digital case store; distribute physical prompts per table.");
        if (!allowSecrets && isOrganizer) {
            notes.add("Secret Scene titles are omitted from this printout (enable 'Include secret titles' for host-only envelope).");
        }

        return new PrintPacketResponse(
                event.getId(),
                event.getName(),
                event.getTheme(),
                event.getStartsAt(),
                event.getTimezone(),
                event.getVenue(),
                effectiveScope,
                Instant.now(),
                teams,
                games,
                fixtures,
                clues,
                secretTitles,
                notes
        );
    }

    private List<PrintFixtureView> loadPrintableFixtures(Jwt jwt, UUID eventId) {
        List<ScheduleSummary> schedules = scheduleService.listSchedules(jwt, eventId);
        Optional<ScheduleSummary> chosen = schedules.stream()
                .filter(s -> "published".equals(s.status()))
                .findFirst()
                .or(() -> schedules.stream().findFirst());

        if (chosen.isEmpty()) {
            return List.of();
        }

        ScheduleDetail detail = scheduleService.getSchedule(jwt, eventId, chosen.get().id());
        return detail.fixtures().stream()
                .filter(f -> !"voided".equals(f.status()))
                .map(f -> new PrintFixtureView(
                        f.roundNumber(),
                        f.startAt(),
                        f.endAt(),
                        f.gameName(),
                        f.resourceName(),
                        f.teams().stream()
                                .map(ft -> new PrintFixtureTeamView(ft.teamName(), ft.side()))
                                .toList()
                ))
                .toList();
    }

    private static String normalizeSheetKey(String raw) {
        return raw.trim().toLowerCase(Locale.ROOT);
    }

    private static String formatLedgerReason(String sheetKey, int rowNumber, String reason) {
        String prefix = "[Offline " + sheetKey + " #" + rowNumber + "] ";
        String full = prefix + reason;
        return full.length() <= MAX_LEDGER_REASON_LEN
                ? full
                : full.substring(0, MAX_LEDGER_REASON_LEN);
    }

    private static String describeMismatch(
            ExistingSyncedRow existing,
            UUID teamId,
            String category,
            BigDecimal delta,
            String reason
    ) {
        List<String> diffs = new ArrayList<>();
        if (!existing.teamId().equals(teamId)) {
            diffs.add("team differs");
        }
        if (!existing.category().equals(category)) {
            diffs.add("category was '" + existing.category() + "'");
        }
        if (existing.delta().compareTo(delta) != 0) {
            diffs.add("delta was " + existing.delta().stripTrailingZeros().toPlainString());
        }
        if (!existing.reason().equals(reason)) {
            diffs.add("reason differs");
        }
        return diffs.isEmpty()
                ? null
                : "Already synced with different values (" + String.join(", ", diffs) + ").";
    }

    private ExistingSyncedRow findExistingRow(UUID eventId, String normalizedSheetKey, int rowNumber) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery("""
                SELECT team_id, category, delta, reason, score_entry_id
                FROM murise.offline_score_rows
                WHERE event_id = :eventId
                  AND lower(btrim(sheet_key)) = :sheetKey
                  AND row_number = :rowNumber
                """)
                .setParameter("eventId", eventId)
                .setParameter("sheetKey", normalizedSheetKey)
                .setParameter("rowNumber", rowNumber)
                .getResultList();

        if (rows.isEmpty()) {
            return null;
        }
        Object[] r = rows.getFirst();
        return new ExistingSyncedRow(
                toUuid(r[0]),
                r[1].toString(),
                r[2] instanceof BigDecimal bd ? bd : new BigDecimal(r[2].toString()),
                r[3].toString(),
                toUuid(r[4])
        );
    }

    private static UUID toUuid(Object val) {
        return val instanceof UUID u ? u : UUID.fromString(val.toString());
    }

    private record ExistingSyncedRow(
            UUID teamId,
            String category,
            BigDecimal delta,
            String reason,
            UUID scoreEntryId
    ) {
    }
}