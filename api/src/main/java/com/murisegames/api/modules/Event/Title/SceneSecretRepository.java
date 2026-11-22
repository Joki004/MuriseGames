package com.murisegames.api.modules.Event.Title;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SceneSecretRepository extends JpaRepository<SceneSecret, UUID> {

    List<SceneSecret> findByEventId(UUID eventId);
}