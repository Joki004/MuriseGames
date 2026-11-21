package com.murisegames.api.modules.Event.Live;

import com.murisegames.api.modules.Event.Membership.EventMembershipService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventLiveServiceTest {

    @Mock
    private EventStateRepository stateRepository;

    @Mock
    private EventMembershipService memberships;

    @Mock
    private EntityManager entityManager;

    @Mock
    private Query query;



    private Instant fixedNow;
    private EventLiveService service;
    private UUID eventId;
    private UUID organizerId;

    @BeforeEach
    void setUp() {
        fixedNow = Instant.parse("2026-11-21T20:00:00Z");
        service = new EventLiveService(
                stateRepository,
                memberships,
                entityManager,
                Clock.fixed(fixedNow, ZoneOffset.UTC)
        );
        eventId = UUID.randomUUID();
        organizerId = UUID.randomUUID();
    }

    private void stubEventMeta(String status, String scoreboardVisibility) {
        Query readyQuery = mock(Query.class);
        when(entityManager.createNativeQuery(contains("from murise.events"))).thenReturn(query);
        when(query.setParameter(eq("eventId"), eq(eventId))).thenReturn(query);
        when(query.getResultList()).thenReturn(List.<Object[]>of(new Object[]{status, scoreboardVisibility}));
        when(entityManager.createNativeQuery(contains("event_team_readiness"))).thenReturn(readyQuery);
        when(readyQuery.setParameter(eq("eventId"), eq(eventId))).thenReturn(readyQuery);
        when(readyQuery.getResultList()).thenReturn(List.of());
    }

    @Test
    void revokedUserIsDeniedOnNextSnapshotRead() {
        UUID revokedUser = UUID.randomUUID();
        when(memberships.requireAccess(eventId, revokedUser))
                .thenThrow(new EntityNotFoundException("Event not found."));

        assertThatThrownBy(() -> service.getSnapshot(eventId, revokedUser))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void pauseAndResumePreserveRemainingTimeUsingServerClock() {
        when(memberships.requireAccess(eventId, organizerId)).thenReturn(Set.of("ORGANIZER"));
        stubEventMeta("live", "public");

        EventState state = new EventState();
        state.setEventId(eventId);
        state.setPhase("active_round");
        state.setDeadlineAt(fixedNow.plusSeconds(45));
        state.setVersion(2);

        when(stateRepository.lockByEventId(eventId)).thenReturn(Optional.of(state));
        when(stateRepository.saveAndFlush(any(EventState.class))).thenAnswer(inv -> inv.getArgument(0));

        EventSnapshotResponse paused = service.pauseTimer(eventId, organizerId);
        assertThat(paused.paused()).isTrue();
        assertThat(paused.pausedRemainingMs()).isEqualTo(45_000L);
        assertThat(paused.deadlineAt()).isNull();
        assertThat(paused.version()).isEqualTo(3L);
        assertThat(paused.serverTime()).isEqualTo(fixedNow);

        EventSnapshotResponse resumed = service.resumeTimer(eventId, organizerId);
        assertThat(resumed.paused()).isFalse();
        assertThat(resumed.pausedRemainingMs()).isNull();
        assertThat(resumed.deadlineAt()).isEqualTo(fixedNow.plusMillis(45_000L));
        assertThat(resumed.version()).isEqualTo(4L);
    }

    @Test
    void playerCannotPauseTimer() {
        UUID playerId = UUID.randomUUID();
        doThrow(new AccessDeniedException("Organizer permission required."))
                .when(memberships).requireOrganizer(eventId, playerId);

        assertThatThrownBy(() -> service.pauseTimer(eventId, playerId))
                .isInstanceOf(AccessDeniedException.class);
    }
}