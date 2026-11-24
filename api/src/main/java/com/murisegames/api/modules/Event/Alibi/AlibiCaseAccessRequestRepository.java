package com.murisegames.api.modules.Event.Alibi;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AlibiCaseAccessRequestRepository
        extends JpaRepository<AlibiCaseAccessRequest, UUID> {

    List<AlibiCaseAccessRequest> findByEventIdOrderByCreatedAtDesc(UUID eventId);

    List<AlibiCaseAccessRequest> findByEventIdAndRequesterIdOrderByCreatedAtDesc(
            UUID eventId, UUID requesterId);

    boolean existsByCaseIdAndRequesterIdAndStatus(UUID caseId, UUID requesterId, String status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select r from AlibiCaseAccessRequest r
            where r.id = :requestId and r.eventId = :eventId
            """)
    Optional<AlibiCaseAccessRequest> lockById(
            @Param("eventId") UUID eventId,
            @Param("requestId") UUID requestId
    );
}
