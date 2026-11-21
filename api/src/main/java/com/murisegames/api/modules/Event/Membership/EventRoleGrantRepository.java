package com.murisegames.api.modules.Event.Membership;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EventRoleGrantRepository
        extends JpaRepository<EventRoleGrant, UUID> {

    List<EventRoleGrant> findByEventIdAndRevokedAtIsNull(UUID eventId);

    Optional<EventRoleGrant> findByEventIdAndMembershipIdAndRoleAndRevokedAtIsNull(
            UUID eventId,
            UUID membershipId,
            String role
    );

    @Query("""
        select count(g)
        from EventRoleGrant g, EventMembership m
        where g.eventId = :eventId
          and g.role = :role
          and g.revokedAt is null
          and m.id = g.membershipId
          and m.eventId = g.eventId
          and m.status = 'active'
        """)
    long countActiveGrants(
            @Param("eventId") UUID eventId,
            @Param("role") String role
    );
}