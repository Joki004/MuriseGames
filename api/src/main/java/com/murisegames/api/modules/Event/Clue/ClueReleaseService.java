package com.murisegames.api.modules.Event.Clue;

import com.murisegames.api.modules.Event.EventRepository;
import com.murisegames.api.modules.Event.Membership.EventMembershipService;
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
import java.util.List;
import java.util.UUID;

import static com.murisegames.api.modules.Event.Clue.ClueReleaseTypes.*;

@Service
@RequiredArgsConstructor
public class ClueReleaseService {

    private final ClueService clueService;
    private final EventRepository eventRepository;
    private final EventMembershipService membershipService;
    private final UserService userService;
    private final EntityManager entityManager;

    /**
     * Public/participant/TV read: returns ONLY clues for stages <= current_stage.
     * Unreleased clue text is never queried or returned.
     */
    @Transactional(readOnly = true)
    public ReleasedCluesResponse getReleasedClues(Jwt jwt, UUID eventId) {
        UUID actorId = userService.extractUserId(jwt);
        membershipService.requireAccess(eventId, actorId);

        StateRow state = readStateOrDefault(eventId);
        return buildReleasedResponse(eventId, state);
    }

    /**
     * Atomically validates that every active team has an approved clue for the next stage,
     * records the append-only release, and advances guessing_state in one transaction.
     */
    @Transactional
    public ReleasedCluesResponse releaseNextStage(
            Jwt jwt,
            UUID eventId,
            StageCommandRequest request
    ) {
        UUID actorId = userService.extractUserId(jwt);
        membershipService.requireOrganizer(eventId, actorId);

        StateRow state = lockOrInitState(eventId);

        // Idempotent retry check: if this requestId already released a stage, return current released state.
        if (releaseExistsForRequest(eventId, request.requestId())) {
            return buildReleasedResponse(eventId, state);
        }

        validateExpectedState(state, request.expectedStage(), request.expectedVersion(), true);

        int nextStage = state.currentStage() + 1;
        if (nextStage > ClueService.STAGES) {
            throw conflict("All clue stages have already been released.");
        }

        clueService.requireStageReleasable(eventId, nextStage);

        entityManager.createNativeQuery("""
                INSERT INTO murise.clue_releases (event_id, stage, request_id, released_by)
                VALUES (:eventId, :stage, :requestId, :releasedBy)
                """)
                .setParameter("eventId", eventId)
                .setParameter("stage", nextStage)
                .setParameter("requestId", request.requestId())
                .setParameter("releasedBy", actorId)
                .executeUpdate();

        long nextVersion = state.version() + 1;
        entityManager.createNativeQuery("""
                UPDATE murise.guessing_state
                SET current_stage = :stage,
                    version       = :version
                WHERE event_id = :eventId
                """)
                .setParameter("stage", nextStage)
                .setParameter("version", nextVersion)
                .setParameter("eventId", eventId)
                .executeUpdate();

        recordAudit(eventId, actorId, "CLUE_STAGE_RELEASED", nextStage, nextVersion);

        return buildReleasedResponse(
                eventId,
                new StateRow(nextStage, state.status(), nextVersion)
        );
    }

    /**
     * Locks all guessing before the first title reveal.
     */
    @Transactional
    public ReleasedCluesResponse closeGuessing(
            Jwt jwt,
            UUID eventId,
            StageCommandRequest request
    ) {
        UUID actorId = userService.extractUserId(jwt);
        membershipService.requireOrganizer(eventId, actorId);

        StateRow state = lockOrInitState(eventId);

        if ("closed".equals(state.status())) {
            return buildReleasedResponse(eventId, state);
        }

        validateExpectedState(state, request.expectedStage(), request.expectedVersion(), true);

        long nextVersion = state.version() + 1;
        entityManager.createNativeQuery("""
                UPDATE murise.guessing_state
                SET status  = 'closed',
                    version = :version
                WHERE event_id = :eventId
                """)
                .setParameter("version", nextVersion)
                .setParameter("eventId", eventId)
                .executeUpdate();

        recordAudit(eventId, actorId, "GUESSING_CLOSED", state.currentStage(), nextVersion);

        return buildReleasedResponse(
                eventId,
                new StateRow(state.currentStage(), "closed", nextVersion)
        );
    }

    /**
     * Shared lock helper for guess submission (GM-015):
     * locks the exact same guessing_state row and rejects stale stage/version with 409.
     */
    public StateRow lockAndValidateForGuess(
            UUID eventId,
            int expectedStage,
            long expectedVersion
    ) {
        StateRow state = lockOrInitState(eventId);
        validateExpectedState(state, expectedStage, expectedVersion, true);
        return state;
    }

    /**
     * Shared lock helper for title reveals (GM-018):
     * locks the same event + guessing_state rows and refuses while guessing is open.
     */
    public StateRow lockForReveal(UUID eventId) {
        StateRow state = lockOrInitState(eventId);
        requireClosedForReveal(state);
        return state;
    }

    static void requireClosedForReveal(StateRow current) {
        if (!"closed".equals(current.status())) {
            throw conflict("Close all guessing before revealing any title.");
        }
    }

    static void validateExpectedState(
            StateRow current,
            int expectedStage,
            long expectedVersion,
            boolean requireOpen
    ) {
        if (requireOpen && !"open".equals(current.status())) {
            throw conflict("Guessing is closed. No further stage changes or guesses are accepted.");
        }
        if (current.currentStage() != expectedStage || current.version() != expectedVersion) {
            throw conflict(
                    "Guessing stage changed (current stage " + current.currentStage()
                            + ", version " + current.version() + "). Refresh and try again."
            );
        }
    }

    private StateRow lockOrInitState(UUID eventId) {
        eventRepository.lockById(eventId)
                .orElseThrow(() -> new EntityNotFoundException("Event not found."));

        entityManager.createNativeQuery("""
                INSERT INTO murise.guessing_state (event_id, current_stage, status, version)
                VALUES (:eventId, 0, 'open', 0)
                ON CONFLICT (event_id) DO NOTHING
                """)
                .setParameter("eventId", eventId)
                .executeUpdate();

        Object[] row = (Object[]) entityManager.createNativeQuery("""
                SELECT current_stage, status, version
                FROM murise.guessing_state
                WHERE event_id = :eventId
                FOR UPDATE
                """)
                .setParameter("eventId", eventId)
                .getSingleResult();

        return new StateRow(
                ((Number) row[0]).intValue(),
                row[1].toString(),
                ((Number) row[2]).longValue()
        );
    }

    private StateRow readStateOrDefault(UUID eventId) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery("""
                SELECT current_stage, status, version
                FROM murise.guessing_state
                WHERE event_id = :eventId
                """)
                .setParameter("eventId", eventId)
                .getResultList();

        if (rows.isEmpty()) {
            return new StateRow(0, "open", 0L);
        }
        Object[] row = rows.getFirst();
        return new StateRow(
                ((Number) row[0]).intValue(),
                row[1].toString(),
                ((Number) row[2]).longValue()
        );
    }

    private boolean releaseExistsForRequest(UUID eventId, UUID requestId) {
        Number count = (Number) entityManager.createNativeQuery("""
                SELECT count(*)
                FROM murise.clue_releases
                WHERE event_id = :eventId AND request_id = :requestId
                """)
                .setParameter("eventId", eventId)
                .setParameter("requestId", requestId)
                .getSingleResult();
        return count.longValue() > 0;
    }

    private ReleasedCluesResponse buildReleasedResponse(UUID eventId, StateRow state) {
        @SuppressWarnings("unchecked")
        List<Object[]> releaseRows = entityManager.createNativeQuery("""
                SELECT stage, released_by, released_at
                FROM murise.clue_releases
                WHERE event_id = :eventId
                ORDER BY stage ASC
                """)
                .setParameter("eventId", eventId)
                .getResultList();

        List<ReleaseRecord> releases = releaseRows.stream()
                .map(r -> new ReleaseRecord(
                        ((Number) r[0]).intValue(),
                        UUID.fromString(r[1].toString()),
                        toInstant(r[2])
                ))
                .toList();

        List<PublicClue> clues = List.of();
        if (state.currentStage() > 0) {
            @SuppressWarnings("unchecked")
            List<Object[]> clueRows = entityManager.createNativeQuery("""
                    SELECT t.id, t.name, t.color, sc.stage, sc.body
                    FROM murise.scene_clues sc
                    JOIN murise.scene_entries se ON se.id = sc.scene_id
                    JOIN murise.teams t ON t.id = se.team_id
                    WHERE sc.event_id = :eventId
                      AND t.active = true
                      AND sc.approved = true
                      AND sc.stage <= :currentStage
                    ORDER BY sc.stage ASC, t.sort_order ASC, t.name ASC
                    """)
                    .setParameter("eventId", eventId)
                    .setParameter("currentStage", state.currentStage())
                    .getResultList();

            clues = clueRows.stream()
                    .map(r -> new PublicClue(
                            UUID.fromString(r[0].toString()),
                            r[1].toString(),
                            r[2].toString(),
                            ((Number) r[3]).intValue(),
                            r[4].toString()
                    ))
                    .toList();
        }

        return new ReleasedCluesResponse(
                eventId,
                state.currentStage(),
                state.status(),
                state.version(),
                releases,
                clues
        );
    }

    private void recordAudit(UUID eventId, UUID actorId, String action, int stage, long version) {
        entityManager.createNativeQuery("""
                INSERT INTO murise.audit_events (
                    event_id, actor_id, action, entity_type, entity_id, details
                )
                VALUES (
                    :eventId, :actorId, :action, 'guessing_state', :eventId,
                    jsonb_build_object('stage', :stage, 'version', :version)
                )
                """)
                .setParameter("eventId", eventId)
                .setParameter("actorId", actorId)
                .setParameter("action", action)
                .setParameter("stage", stage)
                .setParameter("version", version)
                .executeUpdate();
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

    public record StateRow(int currentStage, String status, long version) {
    }
}