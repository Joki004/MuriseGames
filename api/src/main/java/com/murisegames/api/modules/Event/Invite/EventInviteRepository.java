package com.murisegames.api.modules.Event.Invite;

import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EventInviteRepository
        extends JpaRepository<EventInvite, UUID> {

    List<EventInvite> findByEventIdOrderByCreatedAtDescIdDesc(UUID eventId);

    Optional<EventInvite> findByIdAndEventId(UUID id, UUID eventId);

    @Query("""
        select i.eventId
        from EventInvite i
        where i.tokenHash = :tokenHash
        """)
    Optional<UUID> findEventIdByTokenHash(
            @Param("tokenHash") String tokenHash
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select i
        from EventInvite i
        where i.eventId = :eventId
          and i.tokenHash = :tokenHash
        """)
    Optional<EventInvite> lockForRedemption(
            @Param("eventId") UUID eventId,
            @Param("tokenHash") String tokenHash
    );
}