package com.murisegames.api.modules.Event.Clue;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SceneEntryRepository extends JpaRepository<SceneEntry, UUID> {

    List<SceneEntry> findByEventId(UUID eventId);

    Optional<SceneEntry> findByEventIdAndTeamId(UUID eventId, UUID teamId);
}