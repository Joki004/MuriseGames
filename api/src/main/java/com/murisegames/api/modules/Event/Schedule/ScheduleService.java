package com.murisegames.api.modules.Event.Schedule;

import com.murisegames.api.modules.Event.EventRepository;
import com.murisegames.api.modules.Event.Membership.EventMembershipService;
import com.murisegames.api.modules.Event.Team.Team;
import com.murisegames.api.modules.Event.Team.TeamRepository;
import com.murisegames.api.modules.User.UserService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static com.murisegames.api.modules.Event.Schedule.ScheduleTypes.*;

@Service
@RequiredArgsConstructor
public class ScheduleService {

    private final EventRepository eventRepository;
    private final TeamRepository teamRepository;
    private final EventMembershipService membershipService;
    private final UserService userService;
    private final EntityManager entityManager;

    @Transactional(readOnly = true)
    public List<ScheduleSummary> listSchedules(Jwt jwt, UUID eventId) {
        UUID actorId = userService.extractUserId(jwt);
        Set<String> roles = membershipService.requireAccess(eventId, actorId);
        boolean organizer = roles.contains("ORGANIZER");

        String sql = organizer
                ? """
                  SELECT id, version, mode, status, seed, created_at
                  FROM murise.schedule_versions
                  WHERE event_id = :eventId
                  ORDER BY version DESC
                  """
                : """
                  SELECT id, version, mode, status, seed, created_at
                  FROM murise.schedule_versions
                  WHERE event_id = :eventId AND status = 'published'
                  ORDER BY version DESC
                  """;

        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery(sql)
                .setParameter("eventId", eventId)
                .getResultList();

        return rows.stream()
                .map(r -> new ScheduleSummary(
                        UUID.fromString(r[0].toString()),
                        ((Number) r[1]).intValue(),
                        r[2].toString(),
                        r[3].toString(),
                        r[4] != null ? ((Number) r[4]).longValue() : null,
                        toInstant(r[5])
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public ScheduleDetail getSchedule(Jwt jwt, UUID eventId, UUID scheduleId) {
        UUID actorId = userService.extractUserId(jwt);
        Set<String> roles = membershipService.requireAccess(eventId, actorId);
        boolean organizer = roles.contains("ORGANIZER");

        @SuppressWarnings("unchecked")
        List<Object[]> headers = entityManager.createNativeQuery("""
                SELECT version, mode, status, seed
                FROM murise.schedule_versions
                WHERE event_id = :eventId AND id = :scheduleId
                """)
                .setParameter("eventId", eventId)
                .setParameter("scheduleId", scheduleId)
                .getResultList();

        if (headers.isEmpty()) {
            throw new EntityNotFoundException("Schedule version not found.");
        }

        Object[] h = headers.getFirst();
        String status = h[2].toString();
        if (!organizer && !"published".equals(status)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Draft schedules are organizer-only.");
        }

        List<FixtureView> fixtures = loadFixtures(eventId, scheduleId);
        Map<UUID, Map<UUID, Integer>> matrix = buildOpponentMatrix(eventId, fixtures);

        return new ScheduleDetail(
                scheduleId,
                eventId,
                ((Number) h[0]).intValue(),
                h[1].toString(),
                status,
                h[3] != null ? ((Number) h[3]).longValue() : null,
                fixtures,
                matrix,
                List.of()
        );
    }

    @Transactional
    public ScheduleDetail createManualDraft(Jwt jwt, UUID eventId) {
        UUID actorId = userService.extractUserId(jwt);
        membershipService.requireOrganizer(eventId, actorId);
        eventRepository.lockById(eventId)
                .orElseThrow(() -> new EntityNotFoundException("Event not found."));

        UUID scheduleId = insertScheduleVersion(eventId, "manual", "manual-v1", null);
        return getSchedule(jwt, eventId, scheduleId);
    }

    @Transactional
    public ScheduleDetail generateBalancedDraft(
            Jwt jwt,
            UUID eventId,
            GenerateBalancedDraftRequest request
    ) {
        UUID actorId = userService.extractUserId(jwt);
        membershipService.requireOrganizer(eventId, actorId);
        eventRepository.lockById(eventId)
                .orElseThrow(() -> new EntityNotFoundException("Event not found."));

        List<Team> activeTeams =
                teamRepository.findByEventIdAndActiveTrueOrderBySortOrderAscNameAsc(eventId);
        if (activeTeams.isEmpty()) {
            throw badRequest("Add at least one active team before generating a schedule.");
        }

        List<BalancedScheduleGenerator.GameInput> games = loadSelectedGames(eventId, request.eventGameIds());
        List<UUID> teamIds = activeTeams.stream().map(Team::getId).toList();

        var generated = BalancedScheduleGenerator.generate(
                teamIds,
                games,
                request.startAt(),
                request.slotMinutes(),
                request.breakMinutes(),
                request.seed()
        );

        UUID scheduleId = insertScheduleVersion(
                eventId, "balanced", "balanced-v1", generated.seed()
        );

        for (var slot : generated.slots()) {
            insertSlotAndFixture(
                    eventId,
                    scheduleId,
                    slot.eventGameId(),
                    slot.roundNumber(),
                    slot.startAt(),
                    slot.endAt(),
                    slot.teamIds(),
                    slot.bye(),
                    null
            );
        }

        ScheduleDetail detail = getSchedule(jwt, eventId, scheduleId);
        return new ScheduleDetail(
                detail.id(),
                detail.eventId(),
                detail.version(),
                detail.mode(),
                detail.status(),
                detail.seed(),
                detail.fixtures(),
                generated.opponentMatrix(),
                generated.warnings()
        );
    }

    @Transactional
    public ScheduleDetail addFixture(
            Jwt jwt,
            UUID eventId,
            UUID scheduleId,
            AddFixtureRequest request
    ) {
        UUID actorId = userService.extractUserId(jwt);
        membershipService.requireOrganizer(eventId, actorId);
        eventRepository.lockById(eventId)
                .orElseThrow(() -> new EntityNotFoundException("Event not found."));

        requireDraftSchedule(eventId, scheduleId);

        if (!request.endAt().isAfter(request.startAt())) {
            throw badRequest("End time must be after start time.");
        }
        if (request.bye() && request.teamIds().size() != 1) {
            throw badRequest("A bye fixture must have exactly one team.");
        }
        if (new HashSet<>(request.teamIds()).size() != request.teamIds().size()) {
            throw badRequest("A team cannot be assigned to the same fixture twice.");
        }

        if (!request.bye()) {
            checkTeamOverlap(eventId, scheduleId, request.teamIds(), request.startAt(), request.endAt());
        }
        if (request.resourceId() != null) {
            checkResourceOverlap(eventId, scheduleId, request.resourceId(), request.startAt(), request.endAt());
        }

        insertSlotAndFixture(
                eventId,
                scheduleId,
                request.eventGameId(),
                request.roundNumber(),
                request.startAt(),
                request.endAt(),
                request.teamIds(),
                request.bye(),
                request.resourceId()
        );

        return getSchedule(jwt, eventId, scheduleId);
    }

    @Transactional
    public ScheduleDetail deleteFixture(
            Jwt jwt,
            UUID eventId,
            UUID scheduleId,
            UUID fixtureId
    ) {
        UUID actorId = userService.extractUserId(jwt);
        membershipService.requireOrganizer(eventId, actorId);
        eventRepository.lockById(eventId)
                .orElseThrow(() -> new EntityNotFoundException("Event not found."));

        requireDraftSchedule(eventId, scheduleId);

        entityManager.createNativeQuery("""
                DELETE FROM murise.fixture_teams
                WHERE event_id = :eventId AND fixture_id = :fixtureId
                """)
                .setParameter("eventId", eventId)
                .setParameter("fixtureId", fixtureId)
                .executeUpdate();

        entityManager.createNativeQuery("""
                UPDATE murise.fixtures
                SET status = 'voided'
                WHERE event_id = :eventId AND schedule_version_id = :scheduleId AND id = :fixtureId
                """)
                .setParameter("eventId", eventId)
                .setParameter("scheduleId", scheduleId)
                .setParameter("fixtureId", fixtureId)
                .executeUpdate();

        return getSchedule(jwt, eventId, scheduleId);
    }

    @Transactional(readOnly = true)
    public List<ResourceView> listResources(Jwt jwt, UUID eventId) {
        membershipService.requireAccess(eventId, userService.extractUserId(jwt));

        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery("""
                SELECT id, type, name, capacity
                FROM murise.event_resources
                WHERE event_id = :eventId
                ORDER BY type, name
                """)
                .setParameter("eventId", eventId)
                .getResultList();

        return rows.stream()
                .map(r -> new ResourceView(
                        UUID.fromString(r[0].toString()),
                        r[1].toString(),
                        r[2].toString(),
                        ((Number) r[3]).intValue()
                ))
                .toList();
    }

    @Transactional
    public ResourceView createResource(Jwt jwt, UUID eventId, CreateResourceRequest request) {
        membershipService.requireOrganizer(eventId, userService.extractUserId(jwt));

        Object id = entityManager.createNativeQuery("""
                INSERT INTO murise.event_resources (event_id, type, name, capacity)
                VALUES (:eventId, :type, :name, :capacity)
                RETURNING id
                """)
                .setParameter("eventId", eventId)
                .setParameter("type", request.type())
                .setParameter("name", request.name().trim())
                .setParameter("capacity", request.capacity())
                .getSingleResult();

        return new ResourceView(
                UUID.fromString(id.toString()),
                request.type(),
                request.name().trim(),
                request.capacity()
        );
    }

    private UUID insertScheduleVersion(UUID eventId, String mode, String algo, Long seed) {
        Number nextVer = (Number) entityManager.createNativeQuery("""
                SELECT COALESCE(MAX(version), 0) + 1
                FROM murise.schedule_versions
                WHERE event_id = :eventId
                """)
                .setParameter("eventId", eventId)
                .getSingleResult();

        Object id = entityManager.createNativeQuery("""
                INSERT INTO murise.schedule_versions (
                    event_id, mode, algorithm_version, seed, status, version
                )
                VALUES (:eventId, :mode, :algo, :seed, 'draft', :version)
                RETURNING id
                """)
                .setParameter("eventId", eventId)
                .setParameter("mode", mode)
                .setParameter("algo", algo)
                .setParameter("seed", seed)
                .setParameter("version", nextVer.intValue())
                .getSingleResult();

        return UUID.fromString(id.toString());
    }

    private void insertSlotAndFixture(
            UUID eventId,
            UUID scheduleId,
            UUID eventGameId,
            int roundNumber,
            Instant startAt,
            Instant endAt,
            List<UUID> teamIds,
            boolean bye,
            UUID resourceId
    ) {
        Object slotIdObj = entityManager.createNativeQuery("""
                INSERT INTO murise.schedule_slots (
                    event_id, schedule_version_id, event_game_id, start_at, end_at
                )
                VALUES (:eventId, :scheduleId, :eventGameId, :startAt, :endAt)
                RETURNING id
                """)
                .setParameter("eventId", eventId)
                .setParameter("scheduleId", scheduleId)
                .setParameter("eventGameId", eventGameId)
                .setParameter("startAt", startAt)
                .setParameter("endAt", endAt)
                .getSingleResult();
        UUID slotId = UUID.fromString(slotIdObj.toString());

        if (resourceId != null) {
            entityManager.createNativeQuery("""
                    INSERT INTO murise.slot_resource_assignments (event_id, slot_id, resource_id)
                    VALUES (:eventId, :slotId, :resourceId)
                    """)
                    .setParameter("eventId", eventId)
                    .setParameter("slotId", slotId)
                    .setParameter("resourceId", resourceId)
                    .executeUpdate();
        }

        Object fixtureIdObj = entityManager.createNativeQuery("""
                INSERT INTO murise.fixtures (
                    event_id, event_game_id, schedule_version_id, slot_id, round_number, status
                )
                VALUES (:eventId, :eventGameId, :scheduleId, :slotId, :roundNumber, 'scheduled')
                RETURNING id
                """)
                .setParameter("eventId", eventId)
                .setParameter("eventGameId", eventGameId)
                .setParameter("scheduleId", scheduleId)
                .setParameter("slotId", slotId)
                .setParameter("roundNumber", roundNumber)
                .getSingleResult();
        UUID fixtureId = UUID.fromString(fixtureIdObj.toString());

        for (int i = 0; i < teamIds.size(); i++) {
            String side = bye ? "bye" : (teamIds.size() == 2 ? (i == 0 ? "home" : "away") : "team_" + (i + 1));
            entityManager.createNativeQuery("""
                    INSERT INTO murise.fixture_teams (event_id, fixture_id, team_id, side)
                    VALUES (:eventId, :fixtureId, :teamId, :side)
                    """)
                    .setParameter("eventId", eventId)
                    .setParameter("fixtureId", fixtureId)
                    .setParameter("teamId", teamIds.get(i))
                    .setParameter("side", side)
                    .executeUpdate();
        }
    }

    private void requireDraftSchedule(UUID eventId, UUID scheduleId) {
        @SuppressWarnings("unchecked")
        List<Object> rows = entityManager.createNativeQuery("""
                SELECT status
                FROM murise.schedule_versions
                WHERE event_id = :eventId AND id = :scheduleId
                """)
                .setParameter("eventId", eventId)
                .setParameter("scheduleId", scheduleId)
                .getResultList();

        if (rows.isEmpty()) {
            throw new EntityNotFoundException("Schedule version not found.");
        }
        if (!"draft".equals(rows.getFirst().toString())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only draft schedules can be edited.");
        }
    }

    private void checkTeamOverlap(
            UUID eventId,
            UUID scheduleId,
            List<UUID> teamIds,
            Instant startAt,
            Instant endAt
    ) {
        Number overlaps = (Number) entityManager.createNativeQuery("""
                SELECT count(*)
                FROM murise.fixtures f
                JOIN murise.schedule_slots s ON s.id = f.slot_id
                JOIN murise.fixture_teams ft ON ft.fixture_id = f.id
                WHERE f.event_id = :eventId
                  AND f.schedule_version_id = :scheduleId
                  AND f.status <> 'voided'
                  AND ft.side <> 'bye'
                  AND ft.team_id IN (:teamIds)
                  AND s.start_at < :endAt
                  AND s.end_at > :startAt
                """)
                .setParameter("eventId", eventId)
                .setParameter("scheduleId", scheduleId)
                .setParameter("teamIds", teamIds)
                .setParameter("startAt", startAt)
                .setParameter("endAt", endAt)
                .getSingleResult();

        if (overlaps.longValue() > 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "One of the selected teams already has an overlapping fixture in this slot."
            );
        }
    }

    private void checkResourceOverlap(
            UUID eventId,
            UUID scheduleId,
            UUID resourceId,
            Instant startAt,
            Instant endAt
    ) {
        Number overlaps = (Number) entityManager.createNativeQuery("""
                SELECT count(*)
                FROM murise.slot_resource_assignments sra
                JOIN murise.schedule_slots s ON s.id = sra.slot_id
                JOIN murise.fixtures f ON f.slot_id = s.id
                WHERE sra.event_id = :eventId
                  AND s.schedule_version_id = :scheduleId
                  AND sra.resource_id = :resourceId
                  AND f.status <> 'voided'
                  AND s.start_at < :endAt
                  AND s.end_at > :startAt
                """)
                .setParameter("eventId", eventId)
                .setParameter("scheduleId", scheduleId)
                .setParameter("resourceId", resourceId)
                .setParameter("startAt", startAt)
                .setParameter("endAt", endAt)
                .getSingleResult();

        if (overlaps.longValue() > 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This room/station is already booked during that time slot."
            );
        }
    }

    private List<BalancedScheduleGenerator.GameInput> loadSelectedGames(
            UUID eventId,
            List<UUID> eventGameIds
    ) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery("""
                SELECT eg.id, gd.name, gv.participation_mode
                FROM murise.event_games eg
                JOIN murise.game_definitions gd ON gd.id = eg.game_definition_id
                JOIN murise.game_versions gv ON gv.id = eg.game_version_id
                WHERE eg.event_id = :eventId
                  AND eg.id IN (:ids)
                ORDER BY eg.sort_order ASC, eg.created_at ASC
                """)
                .setParameter("eventId", eventId)
                .setParameter("ids", eventGameIds)
                .getResultList();

        if (rows.size() != new HashSet<>(eventGameIds).size()) {
            throw badRequest("One or more selected games do not belong to this event.");
        }

        return rows.stream()
                .map(r -> new BalancedScheduleGenerator.GameInput(
                        UUID.fromString(r[0].toString()),
                        r[1].toString(),
                        r[2].toString()
                ))
                .toList();
    }

    private List<FixtureView> loadFixtures(UUID eventId, UUID scheduleId) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery("""
                SELECT f.id, f.slot_id, f.event_game_id, gd.name, f.round_number, f.status,
                       s.start_at, s.end_at, r.id, r.name,
                       ft.team_id, t.name, ft.side
                FROM murise.fixtures f
                JOIN murise.schedule_slots s ON s.id = f.slot_id
                JOIN murise.event_games eg ON eg.id = f.event_game_id
                JOIN murise.game_definitions gd ON gd.id = eg.game_definition_id
                LEFT JOIN murise.slot_resource_assignments sra ON sra.slot_id = s.id
                LEFT JOIN murise.event_resources r ON r.id = sra.resource_id
                LEFT JOIN murise.fixture_teams ft ON ft.fixture_id = f.id
                LEFT JOIN murise.teams t ON t.id = ft.team_id
                WHERE f.event_id = :eventId
                  AND f.schedule_version_id = :scheduleId
                  AND f.status <> 'voided'
                ORDER BY s.start_at ASC, f.round_number ASC, f.id ASC
                """)
                .setParameter("eventId", eventId)
                .setParameter("scheduleId", scheduleId)
                .getResultList();

        Map<UUID, List<Object[]>> grouped = new LinkedHashMap<>();
        for (Object[] row : rows) {
            grouped.computeIfAbsent(UUID.fromString(row[0].toString()), k -> new ArrayList<>()).add(row);
        }

        List<FixtureView> result = new ArrayList<>();
        for (var entry : grouped.entrySet()) {
            Object[] first = entry.getValue().getFirst();
            List<FixtureTeamView> teams = entry.getValue().stream()
                    .filter(r -> r[10] != null)
                    .map(r -> new FixtureTeamView(
                            UUID.fromString(r[10].toString()),
                            r[11].toString(),
                            r[12].toString()
                    ))
                    .toList();

            result.add(new FixtureView(
                    entry.getKey(),
                    UUID.fromString(first[1].toString()),
                    UUID.fromString(first[2].toString()),
                    first[3].toString(),
                    ((Number) first[4]).intValue(),
                    first[5].toString(),
                    toInstant(first[6]),
                    toInstant(first[7]),
                    first[8] != null ? UUID.fromString(first[8].toString()) : null,
                    first[9] != null ? first[9].toString() : null,
                    teams
            ));
        }
        return result;
    }

    private Map<UUID, Map<UUID, Integer>> buildOpponentMatrix(UUID eventId, List<FixtureView> fixtures) {
        List<Team> teams = teamRepository.findByEventIdAndActiveTrueOrderBySortOrderAscNameAsc(eventId);
        Map<UUID, Map<UUID, Integer>> matrix = new LinkedHashMap<>();
        for (Team a : teams) {
            Map<UUID, Integer> row = new LinkedHashMap<>();
            for (Team b : teams) {
                row.put(b.getId(), 0);
            }
            matrix.put(a.getId(), row);
        }

        for (FixtureView f : fixtures) {
            List<UUID> playing = f.teams().stream()
                    .filter(t -> !"bye".equals(t.side()))
                    .map(FixtureTeamView::teamId)
                    .toList();
            if (playing.size() == 2) {
                UUID a = playing.get(0);
                UUID b = playing.get(1);
                if (matrix.containsKey(a) && matrix.containsKey(b)) {
                    matrix.get(a).merge(b, 1, Integer::sum);
                    matrix.get(b).merge(a, 1, Integer::sum);
                }
            }
        }
        return matrix;
    }

    private static Instant toInstant(Object value) {
        if (value instanceof Instant i) return i;
        if (value instanceof java.sql.Timestamp ts) return ts.toInstant();
        if (value instanceof java.time.OffsetDateTime odt) return odt.toInstant();
        return Instant.parse(value.toString());
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}