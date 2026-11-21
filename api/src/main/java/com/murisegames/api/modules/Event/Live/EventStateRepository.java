package com.murisegames.api.modules.Event.Live;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface EventStateRepository extends JpaRepository<EventState, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from EventState s where s.eventId = :eventId")
    Optional<EventState> lockByEventId(@Param("eventId") UUID eventId);
}