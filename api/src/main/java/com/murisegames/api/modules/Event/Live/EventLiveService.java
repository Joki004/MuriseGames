package com.murisegames.api.modules.Event.Live;

import com.murisegames.api.modules.Event.Membership.EventMembershipService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

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
        // Re-checks current DB grants on every poll; revoked users are denied immediately.
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

        EventState state = stateRepository.lockByEventId(eventId)
                .orElseGet(() -> {
                    EventState created = new EventState();
                    created.setEventId(eventId);
                    created.setVersion(0);
                    return created;
                });

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

    private EventSnapshotResponse toSnapshot(
            UUID eventId,
            EventMeta meta,
            Set<String> roles,
            EventState state,
            Instant serverTime
    ) {
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
                    null
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
                state.getUpdatedAt()
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