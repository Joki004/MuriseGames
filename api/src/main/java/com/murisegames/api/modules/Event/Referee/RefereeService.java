package com.murisegames.api.modules.Event.Referee;

import com.murisegames.api.modules.Audit.AuditService;
import com.murisegames.api.modules.Event.EventRepository;
import com.murisegames.api.modules.Event.Membership.EventMembership;
import com.murisegames.api.modules.Event.Membership.EventMembershipRepository;
import com.murisegames.api.modules.Event.Membership.EventMembershipService;
import com.murisegames.api.modules.Event.Participant.EventParticipantRepository;
import com.murisegames.api.modules.Event.Participant.EventParticipantService;
import com.murisegames.api.modules.Event.Scoring.Adapter.ScorePreviewService;
import com.murisegames.api.modules.User.UserService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.*;

import static com.murisegames.api.modules.Event.Referee.RefereeTypes.*;

/**
 * Referees record official results only for games the host assigned them to,
 * and never for a match their own team plays in. Organizers keep full control.
 */
@Service
@RequiredArgsConstructor
public class RefereeService {

    public static final String ROLE_REFEREE = "REFEREE";
    public static final String ROLE_ORGANIZER = "ORGANIZER";

    private final EventMembershipService membershipService;
    private final EventMembershipRepository membershipRepository;
    private final EventParticipantRepository participantRepository;
    private final EventRepository eventRepository;
    private final ScorePreviewService previewService;
    private final UserService userService;
    private final AuditService auditService;
    private final EntityManager entityManager;

    // =====================================================================
    // Organizer: assign referees to games
    // =====================================================================

    @Transactional(readOnly = true)
    public RefereeBoardResponse board(Jwt jwt, UUID eventId) {
        UUID actorId = userService.extractUserId(jwt);
        membershipService.requireOrganizer(eventId, actorId);
        return buildBoard(eventId);
    }

    @Transactional
    public RefereeBoardResponse assign(Jwt jwt, UUID eventId, AssignRequest request) {
        UUID actorId = userService.extractUserId(jwt);
        membershipService.requireOrganizer(eventId, actorId);
        eventRepository.lockById(eventId)
                .orElseThrow(() -> new EntityNotFoundException("Event not found."));

        if (!hasActiveRefereeRole(eventId, request.membershipId())) {
            throw conflict("Give this person the Referee role first.");
        }
        if (!gameBelongsToEvent(eventId, request.eventGameId())) {
            throw new EntityNotFoundException("Game not found in this event.");
        }

        int inserted = entityManager.createNativeQuery("""
                INSERT INTO murise.referee_assignments (
                    event_id, membership_id, event_game_id, assigned_by
                )
                VALUES (:eventId, :membershipId, :eventGameId, :actorId)
                ON CONFLICT (event_id, membership_id, event_game_id)
                    WHERE revoked_at IS NULL AND round_id IS NULL
                DO NOTHING
                """)
                .setParameter("eventId", eventId)
                .setParameter("membershipId", request.membershipId())
                .setParameter("eventGameId", request.eventGameId())
                .setParameter("actorId", actorId)
                .executeUpdate();

        if (inserted > 0) {
            auditService.record(
                    eventId, actorId, "referee.assigned", "event_game", request.eventGameId(),
                    Map.of("membershipId", request.membershipId().toString())
            );
        }
        return buildBoard(eventId);
    }

    @Transactional
    public RefereeBoardResponse unassign(Jwt jwt, UUID eventId, UUID assignmentId) {
        UUID actorId = userService.extractUserId(jwt);
        membershipService.requireOrganizer(eventId, actorId);
        eventRepository.lockById(eventId)
                .orElseThrow(() -> new EntityNotFoundException("Event not found."));

        int revoked = entityManager.createNativeQuery("""
                UPDATE murise.referee_assignments
                SET revoked_at = now()
                WHERE event_id = :eventId
                  AND id = :assignmentId
                  AND revoked_at IS NULL
                """)
                .setParameter("eventId", eventId)
                .setParameter("assignmentId", assignmentId)
                .executeUpdate();

        if (revoked > 0) {
            auditService.record(
                    eventId, actorId, "referee.unassigned", "referee_assignment", assignmentId,
                    Map.of()
            );
        }
        return buildBoard(eventId);
    }

    // =====================================================================
    // Referee: own desk
    // =====================================================================

    @Transactional(readOnly = true)
    public RefereeDeskResponse desk(Jwt jwt, UUID eventId) {
        UUID actorId = userService.extractUserId(jwt);
        Set<String> roles = membershipService.requireAccess(eventId, actorId);
        if (!roles.contains(ROLE_REFEREE)) {
            throw new AccessDeniedException("Referee permission required.");
        }

        UUID membershipId = membershipIdOf(eventId, actorId);
        List<UUID> gameIds = assignedGameIds(eventId, membershipId);
        UUID myTeamId = activeTeamOf(eventId, actorId);
        boolean published = hasPublishedSchedule(eventId);

        if (gameIds.isEmpty()) {
            return new RefereeDeskResponse(List.of(), List.of(), published);
        }

        var games = previewService.optionsForGames(eventId, gameIds);
        List<DeskFixture> fixtures = published
                ? loadFixtures(eventId, gameIds, myTeamId)
                : List.of();

        return new RefereeDeskResponse(games, fixtures, published);
    }

    // =====================================================================
    // Used by OfficialResultService before recording a result
    // =====================================================================

    /**
     * Organizers may record any result. A referee may record a result only for a
     * game they are assigned to, and never for a match their own team plays in.
     */
    // No @Transactional: this always runs inside the caller's write transaction.
    public void requireCanRecordResult(
            UUID eventId,
            UUID actorId,
            UUID eventGameId,
            Collection<UUID> fixtureTeamIds
    ) {
        Set<String> roles = membershipService.requireAccess(eventId, actorId);
        if (roles.contains(ROLE_ORGANIZER)) {
            return;
        }
        if (!roles.contains(ROLE_REFEREE)) {
            throw new AccessDeniedException("Organizer or assigned referee permission required.");
        }

        UUID membershipId = membershipIdOf(eventId, actorId);
        if (!assignedGameIds(eventId, membershipId).contains(eventGameId)) {
            throw new AccessDeniedException("You are not assigned to referee this game.");
        }

        if (isOwnTeamMatch(activeTeamOf(eventId, actorId), fixtureTeamIds)) {
            throw new AccessDeniedException(
                    "Referees cannot record results for their own team's match.");
        }
    }

    static boolean isOwnTeamMatch(UUID refereeTeamId, Collection<UUID> fixtureTeamIds) {
        return refereeTeamId != null && fixtureTeamIds.contains(refereeTeamId);
    }

    // =====================================================================
    // Internals
    // =====================================================================

    private RefereeBoardResponse buildBoard(UUID eventId) {
        @SuppressWarnings("unchecked")
        List<Object[]> refereeRows = entityManager.createNativeQuery("""
                SELECT DISTINCT m.id, COALESCE(ep.display_name, p.display_name), t.id, t.name
                FROM murise.event_memberships m
                JOIN murise.event_role_grants g
                  ON g.membership_id = m.id AND g.event_id = m.event_id
                 AND g.role = 'REFEREE' AND g.revoked_at IS NULL
                JOIN murise.profiles p ON p.id = m.user_id
                LEFT JOIN murise.event_participants ep
                  ON ep.event_id = m.event_id AND ep.user_id = m.user_id
                 AND ep.status = 'active'
                LEFT JOIN murise.teams t ON t.id = ep.team_id
                WHERE m.event_id = :eventId AND m.status = 'active'
                ORDER BY 2
                """)
                .setParameter("eventId", eventId)
                .getResultList();

        List<RefereeOption> referees = refereeRows.stream()
                .map(r -> new RefereeOption(
                        uuid(r[0]),
                        r[1].toString(),
                        r[2] != null ? uuid(r[2]) : null,
                        r[3] != null ? r[3].toString() : null
                ))
                .toList();

        @SuppressWarnings("unchecked")
        List<Object[]> gameRows = entityManager.createNativeQuery("""
                SELECT eg.id, gd.name
                FROM murise.event_games eg
                JOIN murise.game_definitions gd ON gd.id = eg.game_definition_id
                JOIN murise.game_versions gv ON gv.id = eg.game_version_id
                WHERE eg.event_id = :eventId
                  AND eg.status <> 'voided'
                  AND gv.participation_mode IN ('head_to_head', 'team_solo')
                ORDER BY eg.sort_order, eg.created_at
                """)
                .setParameter("eventId", eventId)
                .getResultList();

        List<GameView> games = gameRows.stream()
                .map(r -> new GameView(uuid(r[0]), r[1].toString()))
                .toList();

        @SuppressWarnings("unchecked")
        List<Object[]> assignmentRows = entityManager.createNativeQuery("""
                SELECT id, membership_id, event_game_id, created_at
                FROM murise.referee_assignments
                WHERE event_id = :eventId
                  AND revoked_at IS NULL
                  AND round_id IS NULL
                ORDER BY created_at
                """)
                .setParameter("eventId", eventId)
                .getResultList();

        List<AssignmentView> assignments = assignmentRows.stream()
                .map(r -> new AssignmentView(uuid(r[0]), uuid(r[1]), uuid(r[2]), toInstant(r[3])))
                .toList();

        return new RefereeBoardResponse(referees, games, assignments);
    }

    private List<DeskFixture> loadFixtures(UUID eventId, List<UUID> gameIds, UUID myTeamId) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery("""
                SELECT f.id, f.event_game_id, gd.name, f.round_number, f.status,
                       s.start_at, s.end_at,
                       (SELECT string_agg(r.name, ', ')
                          FROM murise.slot_resource_assignments sra
                          JOIN murise.event_resources r ON r.id = sra.resource_id
                         WHERE sra.slot_id = s.id),
                       ft.team_id, t.name, ft.side
                FROM murise.fixtures f
                JOIN murise.schedule_versions sv
                  ON sv.id = f.schedule_version_id AND sv.status = 'published'
                JOIN murise.schedule_slots s ON s.id = f.slot_id
                JOIN murise.event_games eg ON eg.id = f.event_game_id
                JOIN murise.game_definitions gd ON gd.id = eg.game_definition_id
                JOIN murise.fixture_teams ft ON ft.fixture_id = f.id
                JOIN murise.teams t ON t.id = ft.team_id
                WHERE f.event_id = :eventId
                  AND f.event_game_id IN (:gameIds)
                ORDER BY s.start_at, gd.name, f.round_number, t.sort_order, t.name
                """)
                .setParameter("eventId", eventId)
                .setParameter("gameIds", gameIds)
                .getResultList();

        Map<UUID, DeskFixtureBuilder> byFixture = new LinkedHashMap<>();
        for (Object[] r : rows) {
            UUID fixtureId = uuid(r[0]);
            DeskFixtureBuilder b = byFixture.computeIfAbsent(fixtureId, id -> new DeskFixtureBuilder(
                    id, uuid(r[1]), r[2].toString(), ((Number) r[3]).intValue(), r[4].toString(),
                    toInstant(r[5]), toInstant(r[6]), r[7] != null ? r[7].toString() : null
            ));
            b.teams.add(new DeskFixtureTeam(uuid(r[8]), r[9].toString(), r[10].toString()));
        }

        return byFixture.values().stream()
                .map(b -> {
                    List<UUID> teamIds = b.teams.stream().map(DeskFixtureTeam::teamId).toList();
                    return new DeskFixture(
                            b.fixtureId, b.eventGameId, b.gameName, b.roundNumber, b.status,
                            b.startAt, b.endAt, b.resourceName, List.copyOf(b.teams),
                            isOwnTeamMatch(myTeamId, teamIds)
                    );
                })
                .toList();
    }

    private static final class DeskFixtureBuilder {
        final UUID fixtureId;
        final UUID eventGameId;
        final String gameName;
        final int roundNumber;
        final String status;
        final Instant startAt;
        final Instant endAt;
        final String resourceName;
        final List<DeskFixtureTeam> teams = new ArrayList<>();

        DeskFixtureBuilder(UUID fixtureId, UUID eventGameId, String gameName, int roundNumber,
                           String status, Instant startAt, Instant endAt, String resourceName) {
            this.fixtureId = fixtureId;
            this.eventGameId = eventGameId;
            this.gameName = gameName;
            this.roundNumber = roundNumber;
            this.status = status;
            this.startAt = startAt;
            this.endAt = endAt;
            this.resourceName = resourceName;
        }
    }

    private UUID membershipIdOf(UUID eventId, UUID userId) {
        return membershipRepository.findByEventIdAndUserId(eventId, userId)
                .map(EventMembership::getId)
                .orElseThrow(() -> new EntityNotFoundException("Event not found."));
    }

    private UUID activeTeamOf(UUID eventId, UUID userId) {
        return participantRepository.findByEventIdAndUserId(eventId, userId)
                .filter(p -> EventParticipantService.STATUS_ACTIVE.equals(p.getStatus()))
                .map(p -> p.getTeamId())
                .orElse(null);
    }

    private List<UUID> assignedGameIds(UUID eventId, UUID membershipId) {
        @SuppressWarnings("unchecked")
        List<Object> rows = entityManager.createNativeQuery("""
                SELECT DISTINCT event_game_id
                FROM murise.referee_assignments
                WHERE event_id = :eventId
                  AND membership_id = :membershipId
                  AND revoked_at IS NULL
                  AND round_id IS NULL
                """)
                .setParameter("eventId", eventId)
                .setParameter("membershipId", membershipId)
                .getResultList();
        return rows.stream().map(RefereeService::uuid).toList();
    }

    private boolean hasActiveRefereeRole(UUID eventId, UUID membershipId) {
        Number count = (Number) entityManager.createNativeQuery("""
                SELECT count(*)
                FROM murise.event_role_grants g
                JOIN murise.event_memberships m ON m.id = g.membership_id
                WHERE g.event_id = :eventId
                  AND g.membership_id = :membershipId
                  AND g.role = 'REFEREE'
                  AND g.revoked_at IS NULL
                  AND m.status = 'active'
                """)
                .setParameter("eventId", eventId)
                .setParameter("membershipId", membershipId)
                .getSingleResult();
        return count.longValue() > 0;
    }

    private boolean gameBelongsToEvent(UUID eventId, UUID eventGameId) {
        Number count = (Number) entityManager.createNativeQuery("""
                SELECT count(*) FROM murise.event_games
                WHERE event_id = :eventId AND id = :eventGameId
                """)
                .setParameter("eventId", eventId)
                .setParameter("eventGameId", eventGameId)
                .getSingleResult();
        return count.longValue() > 0;
    }

    private boolean hasPublishedSchedule(UUID eventId) {
        Number count = (Number) entityManager.createNativeQuery("""
                SELECT count(*) FROM murise.schedule_versions
                WHERE event_id = :eventId AND status = 'published'
                """)
                .setParameter("eventId", eventId)
                .getSingleResult();
        return count.longValue() > 0;
    }

    private static UUID uuid(Object value) {
        return value instanceof UUID u ? u : UUID.fromString(value.toString());
    }

    private static Instant toInstant(Object value) {
        if (value instanceof Instant i) return i;
        if (value instanceof java.sql.Timestamp ts) return ts.toInstant();
        if (value instanceof java.time.OffsetDateTime odt) return odt.toInstant();
        return Instant.parse(value.toString());
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}
