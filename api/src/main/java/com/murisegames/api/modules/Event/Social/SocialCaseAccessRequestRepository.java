package com.murisegames.api.modules.Event.Social;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SocialCaseAccessRequestRepository
        extends JpaRepository<SocialCaseAccessRequest, UUID> {

    List<SocialCaseAccessRequest> findByEventIdOrderByCreatedAtDesc(UUID eventId);

    List<SocialCaseAccessRequest> findByEventIdAndRequesterIdOrderByCreatedAtDesc(
            UUID eventId, UUID requesterId);

    boolean existsByCaseIdAndRequesterIdAndStatus(UUID caseId, UUID requesterId, String status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select r from SocialCaseAccessRequest r
            where r.id = :requestId and r.eventId = :eventId
            """)
    Optional<SocialCaseAccessRequest> lockById(
            @Param("eventId") UUID eventId,
            @Param("requestId") UUID requestId
    );
}