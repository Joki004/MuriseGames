package com.murisegames.api.modules.Event;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;
import java.util.UUID;

public interface EventRepository extends JpaRepository<Event, UUID> {

    @Query("""
            select e
            from Event e
            where exists (
                select m.id
                from EventMembership m, EventRoleGrant g
                where m.eventId = e.id
                  and m.userId = :userId
                  and m.status = 'active'
                  and g.membershipId = m.id
                  and g.eventId = e.id
                  and g.revokedAt is null
            )
            """)
    Page<Event> findAccessibleEvents(
            @Param("userId") UUID userId,
            Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Event e where e.id = :eventId")
    Optional<Event> lockById(@Param("eventId") UUID eventId);
}