package com.murisegames.api.modules.Event.Alibi;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AlibiCaseRepository extends JpaRepository<AlibiCase, UUID> {

    List<AlibiCase> findByEventIdAndArchivedAtIsNullOrderBySortOrderAscCreatedAtAsc(UUID eventId);

    Optional<AlibiCase> findByIdAndEventIdAndArchivedAtIsNull(UUID id, UUID eventId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select c from AlibiCase c
            where c.id = :caseId and c.eventId = :eventId and c.archivedAt is null
            """)
    Optional<AlibiCase> lockActive(@Param("eventId") UUID eventId, @Param("caseId") UUID caseId);

    @Query("select coalesce(max(c.sortOrder), -1) from AlibiCase c where c.eventId = :eventId")
    int maxSortOrder(@Param("eventId") UUID eventId);
}
