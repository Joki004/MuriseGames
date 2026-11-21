package com.murisegames.api.modules.Event.Membership;

import com.murisegames.api.modules.Audit.AuditService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventRoleServiceTest {

    @Mock
    private EventMembershipRepository membershipRepository;

    @Mock
    private EventRoleGrantRepository roleGrantRepository;

    @Mock
    private AuditService auditService;

    @Mock
    private EntityManager entityManager;

    @Mock
    private Query query;

    @InjectMocks
    private EventRoleService service;

    private UUID eventId;
    private UUID organizerId;
    private UUID targetUserId;

    @BeforeEach
    void setUp() {
        eventId = UUID.randomUUID();
        organizerId = UUID.randomUUID();
        targetUserId = UUID.randomUUID();
    }

    private void stubAdminCheck(boolean isAdmin) {
        when(entityManager.createNativeQuery(contains("platform_role_grants"))).thenReturn(query);
        when(query.setParameter(eq("userId"), any())).thenReturn(query);
        when(query.getSingleResult()).thenReturn(isAdmin);
    }

    private void stubEventLock() {
        when(entityManager.createNativeQuery(contains("for update"))).thenReturn(query);
        when(query.setParameter(eq("eventId"), eq(eventId))).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of(eventId));
    }

    @Test
    void organizerCannotGrantGlobalAdminRole() {
        assertThatThrownBy(() -> service.grantRole(eventId, targetUserId, "ADMIN", organizerId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid event role");
    }

    @Test
    void playerCannotGrantRoles() {
        when(membershipRepository.findActiveRoles(eventId, targetUserId)).thenReturn(List.of("PLAYER"));
        stubAdminCheck(false);

        assertThatThrownBy(() -> service.grantRole(eventId, targetUserId, "ORGANIZER", targetUserId))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void outsiderReceivesNotFoundWithoutDisclosingEvent() {
        UUID outsider = UUID.randomUUID();
        when(membershipRepository.findActiveRoles(eventId, outsider)).thenReturn(List.of());
        stubAdminCheck(false);

        assertThatThrownBy(() -> service.grantRole(eventId, targetUserId, "ORGANIZER", outsider))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void revokingLastActiveOrganizerIsRejectedAtomically() {
        when(membershipRepository.findActiveRoles(eventId, organizerId)).thenReturn(List.of("ORGANIZER"));
        stubEventLock();

        EventMembership membership = new EventMembership();
        membership.setId(UUID.randomUUID());
        membership.setEventId(eventId);
        membership.setUserId(organizerId);

        EventRoleGrant grant = new EventRoleGrant();
        grant.setId(UUID.randomUUID());
        grant.setRole("ORGANIZER");

        when(membershipRepository.findByEventIdAndUserId(eventId, organizerId))
                .thenReturn(Optional.of(membership));
        when(roleGrantRepository.findByEventIdAndMembershipIdAndRoleAndRevokedAtIsNull(
                eventId, membership.getId(), "ORGANIZER"))
                .thenReturn(Optional.of(grant));
        when(roleGrantRepository.countActiveGrants(eventId, "ORGANIZER")).thenReturn(1L);

        assertThatThrownBy(() -> service.revokeRole(eventId, organizerId, "ORGANIZER", organizerId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("last active organizer");

        assertThat(grant.getRevokedAt()).isNull();
        verify(auditService, never()).record(any(), any(), any(), any(), any(), any());
    }

    @Test
    void revokingCoOrganizerSucceedsAndWritesAuditEvent() {
        when(membershipRepository.findActiveRoles(eventId, organizerId)).thenReturn(List.of("ORGANIZER"));
        when(membershipRepository.findActiveRoles(eventId, targetUserId)).thenReturn(List.of("PLAYER"));
        stubEventLock();

        EventMembership membership = new EventMembership();
        membership.setId(UUID.randomUUID());
        membership.setEventId(eventId);
        membership.setUserId(targetUserId);
        membership.setStatus("active");

        EventRoleGrant grant = new EventRoleGrant();
        grant.setId(UUID.randomUUID());
        grant.setRole("ORGANIZER");

        when(membershipRepository.findByEventIdAndUserId(eventId, targetUserId))
                .thenReturn(Optional.of(membership));
        when(roleGrantRepository.findByEventIdAndMembershipIdAndRoleAndRevokedAtIsNull(
                eventId, membership.getId(), "ORGANIZER"))
                .thenReturn(Optional.of(grant));
        when(roleGrantRepository.countActiveGrants(eventId, "ORGANIZER")).thenReturn(2L);

        service.revokeRole(eventId, targetUserId, "ORGANIZER", organizerId);

        assertThat(grant.getRevokedAt()).isNotNull();
        assertThat(grant.getRevokedBy()).isEqualTo(organizerId);
        verify(auditService).record(
                eq(eventId),
                eq(organizerId),
                eq("EVENT_ROLE_REVOKED"),
                eq("event_role_grant"),
                eq(grant.getId()),
                argThat((Map<String, Object> d) -> Boolean.FALSE.equals(d.get("adminOverride")))
        );
    }

    @Test
    void platformAdminOverrideIsAudited() {
        UUID adminId = UUID.randomUUID();
        when(membershipRepository.findActiveRoles(eventId, adminId)).thenReturn(List.of());
        when(membershipRepository.findActiveRoles(eventId, targetUserId)).thenReturn(List.of("ORGANIZER"));
        stubAdminCheck(true);
        stubEventLock();

        when(membershipRepository.findByEventIdAndUserId(eventId, targetUserId))
                .thenReturn(Optional.empty());
        when(membershipRepository.saveAndFlush(any(EventMembership.class)))
                .thenAnswer(inv -> {
                    EventMembership m = inv.getArgument(0);
                    m.setId(UUID.randomUUID());
                    return m;
                });
        when(roleGrantRepository.findByEventIdAndMembershipIdAndRoleAndRevokedAtIsNull(
                eq(eventId), any(), eq("ORGANIZER")))
                .thenReturn(Optional.empty());

        service.grantRole(eventId, targetUserId, "ORGANIZER", adminId);

        verify(auditService).record(
                eq(eventId),
                eq(adminId),
                eq("EVENT_ROLE_GRANTED"),
                eq("event_role_grant"),
                any(),
                argThat((Map<String, Object> d) -> Boolean.TRUE.equals(d.get("adminOverride")))
        );
    }
}