package com.murisegames.api.modules.Event.Guess;

import com.murisegames.api.modules.Event.Clue.ClueReleaseService;
import com.murisegames.api.modules.Event.Membership.EventMembershipService;
import com.murisegames.api.modules.Event.Participant.EventParticipant;
import com.murisegames.api.modules.Event.Participant.EventParticipantRepository;
import com.murisegames.api.modules.Event.Participant.EventParticipantService;
import com.murisegames.api.modules.Event.Team.Team;
import com.murisegames.api.modules.Event.Team.TeamRepository;
import com.murisegames.api.modules.User.UserService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.text.Normalizer;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static com.murisegames.api.modules.Event.Guess.GuessTypes.*;

@Service
@RequiredArgsConstructor
public class GuessService {

    private final ClueReleaseService clueReleaseService;
    private final EventMembershipService membershipService;
    private final EventParticipantRepository participantRepository;
    private final TeamRepository teamRepository;
    private final UserService userService;
    private final EntityManager entityManager;

    @Transactional(readOnly = true)
    public CaptainGuessBoardResponse getCaptainBoard(Jwt jwt, UUID eventId) {
        UUID actorId = userService.extractUserId(jwt);
        membershipService.requireAccess(eventId, actorId);

        EventParticipant participant = participantRepository
                .findByEventIdAndUserId(eventId, actorId)
                .filter(p -> EventParticipantService.STATUS_ACTIVE.equals(p.getStatus()))
                .orElse(null);

        StateSnapshot state = readStateOrDefault(eventId);

        if (participant == null || participant.getTeamId() == null) {
            return new CaptainGuessBoardResponse(
                    null,
                    null,
                    false,
                    state.currentStage(),
                    state.status(),
                    state.version(),
                    List.of(),
                    List.of()
            );
        }

        UUID myTeamId = participant.getTeamId();
        List<Team> activeTeams = teamRepository
                .findByEventIdAndActiveTrueOrderBySortOrderAscNameAsc(eventId);

        Team myTeam = activeTeams.stream()
                .filter(t -> t.getId().equals(myTeamId))
                .findFirst()
                .orElse(null);

        if (myTeam == null) {
            return new CaptainGuessBoardResponse(
                    null,
                    null,
                    false,
                    state.currentStage(),
                    state.status(),
                    state.version(),
                    List.of(),
                    List.of()
            );
        }

        Set<UUID> teamsWithScenes = loadTeamsWithScenes(eventId);

        List<OpposingTarget> targets = activeTeams.stream()
                .filter(t -> !t.getId().equals(myTeamId))
                .map(t -> new OpposingTarget(
                        t.getId(),
                        t.getName(),
                        t.getColor(),
                        teamsWithScenes.contains(t.getId())
                ))
                .toList();

        List<GuessReceipt> receipts = loadTeamReceipts(eventId, myTeamId);

        return new CaptainGuessBoardResponse(
                myTeam.getId(),
                myTeam.getName(),
                participant.isCaptain(),
                state.currentStage(),
                state.status(),
                state.version(),
                targets,
                receipts
        );
    }

    @Transactional
    public GuessReceipt submitGuess(
            Jwt jwt,
            UUID eventId,
            SubmitGuessRequest request
    ) {
        UUID actorId = userService.extractUserId(jwt);
        membershipService.requireAccess(eventId, actorId);

        // Lock the shared guessing_state row first and verify stage/version/open status.
        var lockedState = clueReleaseService.lockAndValidateForGuess(
                eventId,
                request.expectedStage(),
                request.expectedVersion()
        );

        // Re-verify current active captain authority after acquiring lock.
        EventParticipant participant = participantRepository
                .findByEventIdAndUserId(eventId, actorId)
                .filter(p -> EventParticipantService.STATUS_ACTIVE.equals(p.getStatus()))
                .orElseThrow(() -> forbidden("You are not an active participant in this event."));

        if (participant.getTeamId() == null) {
            throw forbidden("You must be assigned to a team before submitting guesses.");
        }
        if (!participant.isCaptain()) {
            throw forbidden("Only the current team captain can submit guesses.");
        }

        UUID guessingTeamId = participant.getTeamId();
        Team guessingTeam = teamRepository.findByIdAndEventId(guessingTeamId, eventId)
                .filter(Team::isActive)
                .orElseThrow(() -> conflict("Your team is not active."));

        if (guessingTeam.getId().equals(request.targetTeamId())) {
            throw bad("A team cannot guess its own secret title.");
        }

        // Idempotent retry check for the same requestId.
        GuessReceipt existingByRequest = findReceiptByRequestId(eventId, guessingTeamId, request.requestId());
        if (existingByRequest != null) {
            return existingByRequest;
        }


        if (guessingTeam.getId().equals(request.targetTeamId())) {
            throw bad("A team cannot guess its own secret title.");
        }

        Team targetTeam = teamRepository.findByIdAndEventId(request.targetTeamId(), eventId)
                .filter(Team::isActive)
                .orElseThrow(() -> new EntityNotFoundException("Target team not found in this event."));

        UUID targetSceneId = resolveSceneIdForTeam(eventId, targetTeam.getId());
        int acceptedStage = lockedState.currentStage();

        if (guessExistsForStage(eventId, guessingTeamId, targetSceneId, acceptedStage)) {
            throw conflict(
                    "Your team has already submitted a guess for "
                            + targetTeam.getName() + " in Stage " + acceptedStage + "."
            );
        }

        String rawGuess = request.guess().strip();
        String normalizedGuess = normalizeGuess(rawGuess);
        if (normalizedGuess.isEmpty()) {
            throw bad("Guess must contain at least one letter or number.");
        }

        Object[] inserted;
        try {
            inserted = (Object[]) entityManager.createNativeQuery("""
                    INSERT INTO murise.guesses (
                        event_id, guessing_team_id, target_scene_id, stage,
                        raw_guess, normalized_guess, submitted_by, request_id
                    )
                    VALUES (
                        :eventId, :guessingTeamId, :targetSceneId, :stage,
                        :rawGuess, :normalizedGuess, :submittedBy, :requestId
                    )
                    RETURNING id, submitted_at
                    """)
                    .setParameter("eventId", eventId)
                    .setParameter("guessingTeamId", guessingTeamId)
                    .setParameter("targetSceneId", targetSceneId)
                    .setParameter("stage", acceptedStage)
                    .setParameter("rawGuess", rawGuess)
                    .setParameter("normalizedGuess", normalizedGuess)
                    .setParameter("submittedBy", actorId)
                    .setParameter("requestId", request.requestId())
                    .getSingleResult();
        } catch (DataIntegrityViolationException ex) {
            throw conflict("A guess for this team in Stage " + acceptedStage + " was already recorded.");
        }

        UUID guessId = UUID.fromString(inserted[0].toString());
        Instant submittedAt = toInstant(inserted[1]);

        entityManager.createNativeQuery("""
                INSERT INTO murise.audit_events (
                    event_id, actor_id, action, entity_type, entity_id, details
                )
                VALUES (
                    :eventId, :actorId, 'GUESS_SUBMITTED', 'guess', :guessId,
                    jsonb_build_object(
                        'guessingTeamId', :guessingTeamId,
                        'targetTeamId', :targetTeamId,
                        'stage', :stage
                    )
                )
                """)
                .setParameter("eventId", eventId)
                .setParameter("actorId", actorId)
                .setParameter("guessId", guessId)
                .setParameter("guessingTeamId", guessingTeamId)
                .setParameter("targetTeamId", targetTeam.getId())
                .setParameter("stage", acceptedStage)
                .executeUpdate();

        return new GuessReceipt(
                guessId,
                targetTeam.getId(),
                targetTeam.getName(),
                acceptedStage,
                rawGuess,
                submittedAt,
                "received"
        );
    }

    static String normalizeGuess(String raw) {
        if (raw == null) return "";
        String decomposed = Normalizer.normalize(raw.strip(), Normalizer.Form.NFKD);
        String noMarks = decomposed.replaceAll("\\p{M}+", "");
        return noMarks
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .strip();
    }

    private StateSnapshot readStateOrDefault(UUID eventId) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery("""
                SELECT current_stage, status, version
                FROM murise.guessing_state
                WHERE event_id = :eventId
                """)
                .setParameter("eventId", eventId)
                .getResultList();

        if (rows.isEmpty()) {
            return new StateSnapshot(0, "open", 0L);
        }
        Object[] r = rows.getFirst();
        return new StateSnapshot(
                ((Number) r[0]).intValue(),
                r[1].toString(),
                ((Number) r[2]).longValue()
        );
    }

    private Set<UUID> loadTeamsWithScenes(UUID eventId) {
        @SuppressWarnings("unchecked")
        List<Object> rows = entityManager.createNativeQuery("""
                SELECT team_id
                FROM murise.scene_entries
                WHERE event_id = :eventId
                """)
                .setParameter("eventId", eventId)
                .getResultList();

        return rows.stream()
                .map(r -> UUID.fromString(r.toString()))
                .collect(Collectors.toSet());
    }

    private UUID resolveSceneIdForTeam(UUID eventId, UUID targetTeamId) {
        @SuppressWarnings("unchecked")
        List<Object> rows = entityManager.createNativeQuery("""
                SELECT id
                FROM murise.scene_entries
                WHERE event_id = :eventId AND team_id = :teamId
                """)
                .setParameter("eventId", eventId)
                .setParameter("teamId", targetTeamId)
                .getResultList();

        if (rows.isEmpty()) {
            throw conflict("Target team has not registered a secret scene yet.");
        }
        return UUID.fromString(rows.getFirst().toString());
    }

    private boolean guessExistsForStage(
            UUID eventId,
            UUID guessingTeamId,
            UUID targetSceneId,
            int stage
    ) {
        Number count = (Number) entityManager.createNativeQuery("""
                SELECT count(*)
                FROM murise.guesses
                WHERE event_id = :eventId
                  AND guessing_team_id = :guessingTeamId
                  AND target_scene_id = :targetSceneId
                  AND stage = :stage
                """)
                .setParameter("eventId", eventId)
                .setParameter("guessingTeamId", guessingTeamId)
                .setParameter("targetSceneId", targetSceneId)
                .setParameter("stage", stage)
                .getSingleResult();
        return count.longValue() > 0;
    }

    private GuessReceipt findReceiptByRequestId(
            UUID eventId,
            UUID guessingTeamId,
            UUID requestId
    ) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery("""
                SELECT g.id, t.id, t.name, g.stage, g.raw_guess, g.submitted_at
                FROM murise.guesses g
                JOIN murise.scene_entries se ON se.id = g.target_scene_id
                JOIN murise.teams t ON t.id = se.team_id
                WHERE g.event_id = :eventId
                  AND g.guessing_team_id = :guessingTeamId
                  AND g.request_id = :requestId
                """)
                .setParameter("eventId", eventId)
                .setParameter("guessingTeamId", guessingTeamId)
                .setParameter("requestId", requestId)
                .getResultList();

        if (rows.isEmpty()) return null;
        Object[] r = rows.getFirst();
        return new GuessReceipt(
                UUID.fromString(r[0].toString()),
                UUID.fromString(r[1].toString()),
                r[2].toString(),
                ((Number) r[3]).intValue(),
                r[4].toString(),
                toInstant(r[5]),
                "received"
        );
    }

    private List<GuessReceipt> loadTeamReceipts(UUID eventId, UUID guessingTeamId) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery("""
                SELECT g.id, t.id, t.name, g.stage, g.raw_guess, g.submitted_at
                FROM murise.guesses g
                JOIN murise.scene_entries se ON se.id = g.target_scene_id
                JOIN murise.teams t ON t.id = se.team_id
                WHERE g.event_id = :eventId
                  AND g.guessing_team_id = :guessingTeamId
                ORDER BY g.stage ASC, g.submitted_at ASC
                """)
                .setParameter("eventId", eventId)
                .setParameter("guessingTeamId", guessingTeamId)
                .getResultList();

        return rows.stream()
                .map(r -> new GuessReceipt(
                        UUID.fromString(r[0].toString()),
                        UUID.fromString(r[1].toString()),
                        r[2].toString(),
                        ((Number) r[3]).intValue(),
                        r[4].toString(),
                        toInstant(r[5]),
                        "received"
                ))
                .toList();
    }

    private static Instant toInstant(Object value) {
        if (value instanceof Instant i) return i;
        if (value instanceof java.sql.Timestamp ts) return ts.toInstant();
        if (value instanceof java.time.OffsetDateTime odt) return odt.toInstant();
        return Instant.parse(value.toString());
    }

    private static ResponseStatusException forbidden(String message) {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, message);
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    private static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private record StateSnapshot(int currentStage, String status, long version) {
    }
}