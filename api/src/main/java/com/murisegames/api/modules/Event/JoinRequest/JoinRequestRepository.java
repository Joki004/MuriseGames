package com.murisegames.api.modules.Event.JoinRequest;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JoinRequestRepository
        extends JpaRepository<JoinRequest, UUID> {

    Optional<JoinRequest> findByEventIdAndUserIdAndStatus(
            UUID eventId,
            UUID userId,
            String status
    );

    boolean existsByEventIdAndUserId(UUID eventId, UUID userId);

    List<JoinRequest> findByEventIdOrderByCreatedAtDesc(UUID eventId);

    List<JoinRequest> findByEventIdAndStatusOrderByCreatedAtAsc(
            UUID eventId,
            String status
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select jr from JoinRequest jr where jr.id = :id and jr.eventId = :eventId")
    Optional<JoinRequest> findByIdAndEventIdForUpdate(
            @Param("id") UUID id,
            @Param("eventId") UUID eventId
    );
}