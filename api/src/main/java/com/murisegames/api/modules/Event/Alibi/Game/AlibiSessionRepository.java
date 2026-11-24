package com.murisegames.api.modules.Event.Alibi.Game;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AlibiSessionRepository extends JpaRepository<AlibiSession, UUID> {

    List<AlibiSession> findByEventId(UUID eventId);

    Optional<AlibiSession> findByFixtureId(UUID fixtureId);

    Optional<AlibiSession> findByIdAndEventId(UUID id, UUID eventId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from AlibiSession s where s.id = :id and s.eventId = :eventId")
    Optional<AlibiSession> lock(@Param("eventId") UUID eventId, @Param("id") UUID id);
}
