package com.murisegames.api.modules.Event.Membership;

import com.murisegames.api.modules.Audit.AuditService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EventRoleService {

    private static final Set<String> ALLOWED_EVENT_ROLES =
            Set.of("ORGANIZER", "REFEREE", "PLAYER", "VIEWER");

    private final EventMembershipRepository membershipRepository;
    private final EventRoleGrantRepository roleGrantRepository;
    private final AuditService auditService;
    private final EntityManager entityManager;

    public List<EventMemberRolesResponse> listMemberRoles(UUID eventId, UUID actorId) {
        boolean adminOverride = requireOrganizerOrAdmin(eventId, actorId);
        if (adminOverride) {
            auditService.record(
                    eventId,
                    actorId,
                    "ADMIN_VIEW_EVENT_ROLES",
                    "event",
                    eventId,
                    Map.of("adminOverride", true)
            );
        }

        List<EventMembership> memberships =
                membershipRepository.findByEventIdOrderByCreatedAtAsc(eventId);
        Map<UUID, Set<String>> rolesByMembership = roleGrantRepository
                .findByEventIdAndRevokedAtIsNull(eventId)
                .stream()
                .collect(Collectors.groupingBy(
                        EventRoleGrant::getMembershipId,
                        Collectors.mapping(EventRoleGrant::getRole, Collectors.toSet())
                ));

        return memberships.stream()
                .map(m -> new EventMemberRolesResponse(
                        m.getId(),
                        m.getUserId(),
                        m.getStatus(),
                        rolesByMembership.getOrDefault(m.getId(), Set.of())
                ))
                .toList();
    }

    @Transactional
    public EventMemberRolesResponse grantRole(
            UUID eventId,
            UUID targetUserId,
            String rawRole,
            UUID actorId
    ) {
        String role = normalizeEventRole(rawRole);
        boolean adminOverride = requireOrganizerOrAdmin(eventId, actorId);

        lockEventRow(eventId);

        EventMembership membership = membershipRepository
                .findByEventIdAndUserId(eventId, targetUserId)
                .orElseGet(() -> {
                    EventMembership created = new EventMembership();
                    created.setEventId(eventId);
                    created.setUserId(targetUserId);
                    return created;
                });

        membership.setStatus("active");
        membershipRepository.saveAndFlush(membership);

        Optional<EventRoleGrant> existing = roleGrantRepository
                .findByEventIdAndMembershipIdAndRoleAndRevokedAtIsNull(
                        eventId, membership.getId(), role
                );

        if (existing.isEmpty()) {
            EventRoleGrant grant = new EventRoleGrant();
            grant.setEventId(eventId);
            grant.setMembershipId(membership.getId());
            grant.setRole(role);
            grant.setGrantedBy(actorId);
            roleGrantRepository.saveAndFlush(grant);

            auditService.record(
                    eventId,
                    actorId,
                    "EVENT_ROLE_GRANTED",
                    "event_role_grant",
                    grant.getId(),
                    Map.of(
                            "targetUserId", targetUserId.toString(),
                            "role", role,
                            "adminOverride", adminOverride
                    )
            );
        }

        Set<String> activeRoles = Set.copyOf(
                membershipRepository.findActiveRoles(eventId, targetUserId)
        );
        return new EventMemberRolesResponse(
                membership.getId(),
                membership.getUserId(),
                membership.getStatus(),
                activeRoles
        );
    }

    @Transactional
    public EventMemberRolesResponse revokeRole(
            UUID eventId,
            UUID targetUserId,
            String rawRole,
            UUID actorId
    ) {
        String role = normalizeEventRole(rawRole);
        boolean adminOverride = requireOrganizerOrAdmin(eventId, actorId);

        // Serializes concurrent revocations on this event so the last organizer cannot be removed.
        lockEventRow(eventId);

        EventMembership membership = membershipRepository
                .findByEventIdAndUserId(eventId, targetUserId)
                .orElseThrow(() -> new EntityNotFoundException("Event membership not found."));

        EventRoleGrant grant = roleGrantRepository
                .findByEventIdAndMembershipIdAndRoleAndRevokedAtIsNull(
                        eventId, membership.getId(), role
                )
                .orElseThrow(() -> new EntityNotFoundException("Active role grant not found."));

        if ("ORGANIZER".equals(role)) {
            long activeOrganizers = roleGrantRepository.countActiveGrants(eventId, "ORGANIZER");
            if (activeOrganizers <= 1) {
                throw new IllegalStateException(
                        "Cannot remove the last active organizer from an event."
                );
            }
        }

        grant.setRevokedAt(Instant.now());
        grant.setRevokedBy(actorId);
        roleGrantRepository.saveAndFlush(grant);

        auditService.record(
                eventId,
                actorId,
                "EVENT_ROLE_REVOKED",
                "event_role_grant",
                grant.getId(),
                Map.of(
                        "targetUserId", targetUserId.toString(),
                        "role", role,
                        "adminOverride", adminOverride
                )
        );

        Set<String> activeRoles = Set.copyOf(
                membershipRepository.findActiveRoles(eventId, targetUserId)
        );
        return new EventMemberRolesResponse(
                membership.getId(),
                membership.getUserId(),
                membership.getStatus(),
                activeRoles
        );
    }

    private String normalizeEventRole(String rawRole) {
        if (rawRole == null) {
            throw new IllegalArgumentException("Role is required.");
        }
        String normalized = rawRole.trim().toUpperCase(Locale.ROOT);
        if (!ALLOWED_EVENT_ROLES.contains(normalized)) {
            throw new IllegalArgumentException(
                    "Invalid event role. Allowed roles: " + ALLOWED_EVENT_ROLES
            );
        }
        return normalized;
    }

    /**
     * Returns true if access was granted via platform ADMIN override (not an event ORGANIZER).
     */
    private boolean requireOrganizerOrAdmin(UUID eventId, UUID actorId) {
        Set<String> roles = Set.copyOf(
                membershipRepository.findActiveRoles(eventId, actorId)
        );
        if (roles.contains("ORGANIZER")) {
            return false;
        }
        if (isPlatformAdmin(actorId)) {
            return true;
        }
        if (roles.isEmpty()) {
            throw new EntityNotFoundException("Event not found.");
        }
        throw new AccessDeniedException("Organizer permission required.");
    }

    private boolean isPlatformAdmin(UUID userId) {
        Object result = entityManager.createNativeQuery("""
                select exists(
                    select 1
                    from murise.platform_role_grants
                    where user_id = :userId
                      and role = 'ADMIN'
                      and revoked_at is null
                )
                """)
                .setParameter("userId", userId)
                .getSingleResult();
        return Boolean.TRUE.equals(result);
    }

    private void lockEventRow(UUID eventId) {
        List<?> locked = entityManager.createNativeQuery(
                        "select id from murise.events where id = :eventId for update"
                )
                .setParameter("eventId", eventId)
                .getResultList();
        if (locked.isEmpty()) {
            throw new EntityNotFoundException("Event not found.");
        }
    }
}