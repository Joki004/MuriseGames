package com.murisegames.api.modules.Event.Live;

import com.murisegames.api.modules.Event.Membership.EventMembershipService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class EventLiveService {

    private final EventStateRepository stateRepository;
    private final EventMembershipService memberships;
    private final EntityManager entityManager;
    private final Clock clock;

    @Autowired
    public EventLiveService(
            EventStateRepository stateRepository,
            EventMembershipService memberships,
            EntityManager entityManager
    ) {
        this(stateRepository, memberships, entityManager, Clock.systemUTC());
    }

    EventLiveService(
            EventStateRepository stateRepository,
            EventMembershipService memberships,
            EntityManager entityManager,
            Clock clock
    ) {
        this.stateRepository = stateRepository;
        this.memberships = memberships;
        this.entityManager = entityManager;
        this.clock = clock;
    }

    public EventSnapshotResponse getSnapshot(UUID eventId, UUID actorId) {
        Set<String> roles = memberships.requireAccess(eventId, actorId);
        EventMeta meta = loadEventMeta(eventId);
        EventState state = stateRepository.findById(eventId).orElse(null);

        return toSnapshot(eventId, meta, roles, state, Instant.now(clock));
    }

    @Transactional
    public EventSnapshotResponse updateState(
            UUID eventId,
            EventStateUpdateRequest request,
            UUID actorId
    ) {
        memberships.requireOrganizer(eventId, actorId);
        Set<String> roles = memberships.requireAccess(eventId, actorId);
        EventMeta meta = loadEventMeta(eventId);
        Instant now = Instant.now(clock);

        EventState state = lockOrInitState(eventId);
        state.setCurrentEventGameId(request.currentEventGameId());
        state.setPhase(request.phase().trim());
        state.setPausedRemainingMs(null);
        state.setDeadlineAt(
                request.durationSeconds() != null && request.durationSeconds() > 0
                        ? now.plusSeconds(request.durationSeconds())
                        : null
        );
        state.setVersion(state.getVersion() + 1);

        EventState saved = stateRepository.saveAndFlush(state);
        return toSnapshot(eventId, meta, roles, saved, now);
    }

    @Transactional
    public EventSnapshotResponse pauseTimer(UUID eventId, UUID actorId) {
        memberships.requireOrganizer(eventId, actorId);
        Set<String> roles = memberships.requireAccess(eventId, actorId);
        EventMeta meta = loadEventMeta(eventId);
        Instant now = Instant.now(clock);

        EventState state = stateRepository.lockByEventId(eventId)
                .orElseThrow(() -> new IllegalStateException("No active event state to pause."));

        if (state.getDeadlineAt() == null) {
            throw new IllegalStateException("No running timer to pause.");
        }

        long remainingMs = Math.max(0L, Duration.between(now, state.getDeadlineAt()).toMillis());
        state.setPausedRemainingMs(remainingMs);
        state.setDeadlineAt(null);
        state.setVersion(state.getVersion() + 1);

        EventState saved = stateRepository.saveAndFlush(state);
        return toSnapshot(eventId, meta, roles, saved, now);
    }

    @Transactional
    public EventSnapshotResponse resumeTimer(UUID eventId, UUID actorId) {
        memberships.requireOrganizer(eventId, actorId);
        Set<String> roles = memberships.requireAccess(eventId, actorId);
        EventMeta meta = loadEventMeta(eventId);
        Instant now = Instant.now(clock);

        EventState state = stateRepository.lockByEventId(eventId)
                .orElseThrow(() -> new IllegalStateException("No paused event state to resume."));

        if (state.getPausedRemainingMs() == null) {
            throw new IllegalStateException("Timer is not paused.");
        }

        state.setDeadlineAt(now.plusMillis(state.getPausedRemainingMs()));
        state.setPausedRemainingMs(null);
        state.setVersion(state.getVersion() + 1);

        EventState saved = stateRepository.saveAndFlush(state);
        return toSnapshot(eventId, meta, roles, saved, now);
    }

    @Transactional
    public EventSnapshotResponse setTeamReady(
            UUID eventId,
            UUID teamId,
            boolean ready,
            UUID actorId
    ) {
        Set<String> roles = memberships.requireAccess(eventId, actorId);

        if (!roles.contains("ORGANIZER") && !isActiveTeamMember(eventId, teamId, actorId)) {
            throw new AccessDeniedException("You can only update readiness for your own team.");
        }

        EventMeta meta = loadEventMeta(eventId);
        Instant now = Instant.now(clock);
        EventState state = lockOrInitState(eventId);

        entityManager.createNativeQuery("""
                insert into murise.event_team_readiness (event_id, team_id, ready, updated_by)
                values (:eventId, :teamId, :ready, :actorId)
                on conflict (event_id, team_id)
                do update set ready = excluded.ready, updated_by = excluded.updated_by
                """)
                .setParameter("eventId", eventId)
                .setParameter("teamId", teamId)
                .setParameter("ready", ready)
                .setParameter("actorId", actorId)
                .executeUpdate();

        state.setVersion(state.getVersion() + 1);
        EventState saved = stateRepository.saveAndFlush(state);
        return toSnapshot(eventId, meta, roles, saved, now);
    }

    @Transactional
    public EventSnapshotResponse resetState(UUID eventId, UUID actorId) {
        memberships.requireOrganizer(eventId, actorId);
        Set<String> roles = memberships.requireAccess(eventId, actorId);
        EventMeta meta = loadEventMeta(eventId);
        Instant now = Instant.now(clock);

        EventState state = lockOrInitState(eventId);
        entityManager.createNativeQuery("""
                delete from murise.event_team_readiness
                where event_id = :eventId
                """)
                .setParameter("eventId", eventId)
                .executeUpdate();

        state.setCurrentEventGameId(null);
        state.setPhase("lobby");
        state.setDeadlineAt(null);
        state.setPausedRemainingMs(null);
        state.setVersion(state.getVersion() + 1);

        EventState saved = stateRepository.saveAndFlush(state);
        return toSnapshot(eventId, meta, roles, saved, now);
    }

    private EventState lockOrInitState(UUID eventId) {
        return stateRepository.lockByEventId(eventId)
                .orElseGet(() -> {
                    EventState created = new EventState();
                    created.setEventId(eventId);
                    created.setPhase("lobby");
                    created.setVersion(0);
                    return created;
                });
    }

    private boolean isActiveTeamMember(UUID eventId, UUID teamId, UUID userId) {
        Object result = entityManager.createNativeQuery("""
                select exists(
                    select 1
                    from murise.event_participants
                    where event_id = :eventId
                      and team_id = :teamId
                      and user_id = :userId
                      and status = 'active'
                )
                """)
                .setParameter("eventId", eventId)
                .setParameter("teamId", teamId)
                .setParameter("userId", userId)
                .getSingleResult();
        return Boolean.TRUE.equals(result);
    }

    private Set<UUID> loadReadyTeamIds(UUID eventId) {
        List<?> rows = entityManager.createNativeQuery("""
                select team_id
                from murise.event_team_readiness
                where event_id = :eventId
                  and ready = true
                """)
                .setParameter("eventId", eventId)
                .getResultList();

        return rows.stream()
                .map(row -> row instanceof UUID uuid ? uuid : UUID.fromString(String.valueOf(row)))
                .collect(Collectors.toUnmodifiableSet());
    }

    private EventSnapshotResponse toSnapshot(
            UUID eventId,
            EventMeta meta,
            Set<String> roles,
            EventState state,
            Instant serverTime
    ) {
        Set<UUID> readyTeamIds = loadReadyTeamIds(eventId);

        if (state == null) {
            return new EventSnapshotResponse(
                    eventId,
                    serverTime,
                    0L,
                    meta.status(),
                    meta.scoreboardVisibility(),
                    roles,
                    null,
                    "lobby",
                    null,
                    null,
                    false,
                    null,
                    readyTeamIds
            );
        }

        return new EventSnapshotResponse(
                eventId,
                serverTime,
                state.getVersion(),
                meta.status(),
                meta.scoreboardVisibility(),
                roles,
                state.getCurrentEventGameId(),
                state.getPhase(),
                state.getDeadlineAt(),
                state.getPausedRemainingMs(),
                state.getPausedRemainingMs() != null,
                state.getUpdatedAt(),
                readyTeamIds
        );
    }

    private EventMeta loadEventMeta(UUID eventId) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery("""
                select status, scoreboard_visibility
                from murise.events
                where id = :eventId
                """)
                .setParameter("eventId", eventId)
                .getResultList();

        if (rows.isEmpty()) {
            throw new EntityNotFoundException("Event not found.");
        }

        Object[] row = rows.getFirst();
        return new EventMeta(String.valueOf(row[0]), String.valueOf(row[1]));
    }

    private record EventMeta(String status, String scoreboardVisibility) {
    }
}