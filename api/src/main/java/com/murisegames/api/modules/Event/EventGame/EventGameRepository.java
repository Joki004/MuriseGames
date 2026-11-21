package com.murisegames.api.modules.Event.EventGame;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EventGameRepository extends JpaRepository<EventGame, UUID> {

    List<EventGame> findByEventIdOrderBySortOrderAscCreatedAtAsc(UUID eventId);

    Optional<EventGame> findByIdAndEventId(UUID id, UUID eventId);

    boolean existsByEventIdAndGameDefinitionId(UUID eventId, UUID gameDefinitionId);
}