package com.murisegames.api.modules.Event.Clue;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SceneClueRepository extends JpaRepository<SceneClue, UUID> {

    List<SceneClue> findByEventId(UUID eventId);

    List<SceneClue> findBySceneId(UUID sceneId);
}