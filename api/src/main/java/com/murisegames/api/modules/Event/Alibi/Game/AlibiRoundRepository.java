package com.murisegames.api.modules.Event.Alibi.Game;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AlibiRoundRepository extends JpaRepository<AlibiRound, UUID> {

    List<AlibiRound> findByEventId(UUID eventId);

    List<AlibiRound> findBySessionIdOrderByPairNumberAsc(UUID sessionId);

    Optional<AlibiRound> findByIdAndEventId(UUID id, UUID eventId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from AlibiRound r where r.id = :id and r.eventId = :eventId")
    Optional<AlibiRound> lock(@Param("eventId") UUID eventId, @Param("id") UUID id);
}
