package com.murisegames.api.modules.Event.Membership;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EventMembershipService {

    private final EventMembershipRepository membershipRepository;
    private final EventRoleGrantRepository roleGrantRepository;

    public Set<String> requireAccess(UUID eventId, UUID userId) {
        Set<String> roles = Set.copyOf(
                membershipRepository.findActiveRoles(eventId, userId)
        );

        if (roles.isEmpty()) {
            // Do not disclose whether an inaccessible event exists.
            throw new EntityNotFoundException("Event not found.");
        }

        return roles;
    }

    public void requireOrganizer(UUID eventId, UUID userId) {
        if (!requireAccess(eventId, userId).contains("ORGANIZER")) {
            throw new AccessDeniedException("Organizer permission required.");
        }
    }

    @Transactional
    public EventMembership grantPlayerMembership(
            UUID eventId,
            UUID userId,
            UUID grantedBy
    ) {
        EventMembership membership = membershipRepository
                .findByEventIdAndUserId(eventId, userId)
                .orElseGet(() -> {
                    EventMembership created = new EventMembership();
                    created.setEventId(eventId);
                    created.setUserId(userId);
                    return created;
                });

        membership.setStatus("active");
        membershipRepository.saveAndFlush(membership);

        List<String> activeRoles =
                membershipRepository.findActiveRoles(eventId, userId);

        if (!activeRoles.contains("PLAYER")) {
            EventRoleGrant playerGrant = new EventRoleGrant();
            playerGrant.setEventId(eventId);
            playerGrant.setMembershipId(membership.getId());
            playerGrant.setRole("PLAYER");
            playerGrant.setGrantedBy(grantedBy);
            roleGrantRepository.saveAndFlush(playerGrant);
        }

        return membership;
    }
}