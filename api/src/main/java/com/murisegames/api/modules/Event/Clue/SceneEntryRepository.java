package com.murisegames.api.modules.Event.Clue;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SceneEntryRepository extends JpaRepository<SceneEntry, UUID> {

    List<SceneEntry> findByEventId(UUID eventId);

    Optional<SceneEntry> findByEventIdAndTeamId(UUID eventId, UUID teamId);

    /** GM-018: true once the host has revealed this scene's title. */
    @Query(value = """
            SELECT EXISTS (
                SELECT 1
                FROM murise.scene_reveals
                WHERE event_id = :eventId AND scene_id = :sceneId
            )
            """, nativeQuery = true)
    boolean isRevealed(@Param("eventId") UUID eventId, @Param("sceneId") UUID sceneId);
}
