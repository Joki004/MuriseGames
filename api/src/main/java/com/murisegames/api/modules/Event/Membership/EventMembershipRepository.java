package com.murisegames.api.modules.Event.Membership;

import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.List;

import java.util.UUID;

public interface EventMembershipRepository
        extends JpaRepository<EventMembership, UUID> {

    @Query("""
        select distinct g.role
        from EventMembership m, EventRoleGrant g
        where m.eventId = :eventId
          and m.userId = :userId
          and m.status = 'active'
          and g.eventId = m.eventId
          and g.membershipId = m.id
          and g.revokedAt is null
        """)
    List<String> findActiveRoles(
            @Param("eventId") UUID eventId,
            @Param("userId") UUID userId
    );

    List<EventMembership> findByEventIdOrderByCreatedAtAsc(UUID eventId);

    Optional<EventMembership> findByEventIdAndUserId(
            UUID eventId,
            UUID userId
    );
}