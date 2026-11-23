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
import java.util.*;
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
        state.setCurrentWindowStartAt(request.windowStartAt());
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
        state.setCurrentWindowStartAt(null);
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
        GuessingView guessing = loadGuessing(eventId);

        if (state == null) {
            return new EventSnapshotResponse(
                    eventId,
                    serverTime,
                    0L,
                    meta.status(),
                    meta.scoreboardVisibility(),
                    roles,
                    null,
                    null,
                    "lobby",
                    null,
                    null,
                    false,
                    null,
                    readyTeamIds,
                    List.of(),
                    guessing.stage(),
                    guessing.status(),
                    guessing.version()
            );
        }

        List<EventSnapshotResponse.ActiveFixtureView> activeFixtures =
                loadActiveFixtures(eventId, state.getCurrentEventGameId(), state.getCurrentWindowStartAt());

        return new EventSnapshotResponse(
                eventId,
                serverTime,
                state.getVersion(),
                meta.status(),
                meta.scoreboardVisibility(),
                roles,
                state.getCurrentEventGameId(),
                state.getCurrentWindowStartAt(),
                state.getPhase(),
                state.getDeadlineAt(),
                state.getPausedRemainingMs(),
                state.getPausedRemainingMs() != null,
                state.getUpdatedAt(),
                readyTeamIds,
                activeFixtures,
                guessing.stage(),
                guessing.status(),
                guessing.version()
        );
    }

    /** Public guessing state only: stage, open/closed and version. Never clue text. */
    private record GuessingView(int stage, String status, long version) {
    }

    private GuessingView loadGuessing(UUID eventId) {
        List<?> rows = entityManager.createNativeQuery("""
                SELECT current_stage, status, version
                FROM murise.guessing_state
                WHERE event_id = :eventId
                """)
                .setParameter("eventId", eventId)
                .getResultList();

        if (rows == null || rows.isEmpty()) {
            return new GuessingView(0, "open", 0L);
        }
        Object[] row = (Object[]) rows.getFirst();
        return new GuessingView(
                ((Number) row[0]).intValue(),
                row[1].toString(),
                ((Number) row[2]).longValue()
        );
    }

    private List<EventSnapshotResponse.ActiveFixtureView> loadActiveFixtures(
            UUID eventId,
            UUID currentEventGameId,
            Instant windowStartAt
    ) {
        if (currentEventGameId == null && windowStartAt == null) {
            return List.of();
        }

        var scheduleQuery = entityManager.createNativeQuery("""
                select id
                from murise.schedule_versions
                where event_id = :eventId
                  and status in ('published', 'draft')
                order by case when status = 'published' then 0 else 1 end asc,
                         version desc
                limit 1
                """);
        if (scheduleQuery == null) {
            return List.of();
        }

        @SuppressWarnings("unchecked")
        List<Object> scheduleIds = scheduleQuery
                .setParameter("eventId", eventId)
                .getResultList();

        if (scheduleIds.isEmpty()) {
            return List.of();
        }

        UUID scheduleId = UUID.fromString(String.valueOf(scheduleIds.getFirst()));

        String sql = windowStartAt != null
                ? """
                  select f.id, f.event_game_id, gd.name, gd.code, f.round_number, f.status,
                         s.start_at, s.end_at, r.name,
                         ft.team_id, t.name, t.color, ft.side
                  from murise.fixtures f
                  join murise.schedule_slots s on s.id = f.slot_id
                  join murise.event_games eg on eg.id = f.event_game_id
                  join murise.game_definitions gd on gd.id = eg.game_definition_id
                  left join murise.slot_resource_assignments sra on sra.slot_id = s.id
                  left join murise.event_resources r on r.id = sra.resource_id
                  left join murise.fixture_teams ft on ft.fixture_id = f.id
                  left join murise.teams t on t.id = ft.team_id
                  where f.event_id = :eventId
                    and f.schedule_version_id = :scheduleId
                    and s.start_at = :windowStartAt
                    and f.status <> 'voided'
                  order by f.round_number asc, f.id asc
                  """
                : """
                  select f.id, f.event_game_id, gd.name, gd.code, f.round_number, f.status,
                         s.start_at, s.end_at, r.name,
                         ft.team_id, t.name, t.color, ft.side
                  from murise.fixtures f
                  join murise.schedule_slots s on s.id = f.slot_id
                  join murise.event_games eg on eg.id = f.event_game_id
                  join murise.game_definitions gd on gd.id = eg.game_definition_id
                  left join murise.slot_resource_assignments sra on sra.slot_id = s.id
                  left join murise.event_resources r on r.id = sra.resource_id
                  left join murise.fixture_teams ft on ft.fixture_id = f.id
                  left join murise.teams t on t.id = ft.team_id
                  where f.event_id = :eventId
                    and f.schedule_version_id = :scheduleId
                    and f.event_game_id = :eventGameId
                    and f.status <> 'voided'
                  order by s.start_at asc, f.round_number asc, f.id asc
                  """;

        var query = entityManager.createNativeQuery(sql)
                .setParameter("eventId", eventId)
                .setParameter("scheduleId", scheduleId);

        if (windowStartAt != null) {
            query.setParameter("windowStartAt", windowStartAt);
        } else {
            query.setParameter("eventGameId", currentEventGameId);
        }

        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();

        Map<UUID, List<Object[]>> grouped = new LinkedHashMap<>();
        for (Object[] row : rows) {
            grouped.computeIfAbsent(UUID.fromString(String.valueOf(row[0])), k -> new ArrayList<>())
                    .add(row);
        }

        List<EventSnapshotResponse.ActiveFixtureView> result = new ArrayList<>();
        for (var entry : grouped.entrySet()) {
            Object[] first = entry.getValue().getFirst();
            List<EventSnapshotResponse.ActiveFixtureTeamView> teams = entry.getValue().stream()
                    .filter(r -> r[9] != null)
                    .map(r -> new EventSnapshotResponse.ActiveFixtureTeamView(
                            UUID.fromString(String.valueOf(r[9])),
                            String.valueOf(r[10]),
                            String.valueOf(r[11]),
                            String.valueOf(r[12])
                    ))
                    .toList();

            result.add(new EventSnapshotResponse.ActiveFixtureView(
                    entry.getKey(),
                    UUID.fromString(String.valueOf(first[1])),
                    String.valueOf(first[2]),
                    String.valueOf(first[3]),
                    ((Number) first[4]).intValue(),
                    String.valueOf(first[5]),
                    toInstant(first[6]),
                    toInstant(first[7]),
                    first[8] != null ? String.valueOf(first[8]) : null,
                    teams
            ));
        }
        return result;
    }

    private static Instant toInstant(Object value) {
        if (value instanceof Instant i) return i;
        if (value instanceof java.sql.Timestamp ts) return ts.toInstant();
        if (value instanceof java.time.OffsetDateTime odt) return odt.toInstant();
        return Instant.parse(String.valueOf(value));
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