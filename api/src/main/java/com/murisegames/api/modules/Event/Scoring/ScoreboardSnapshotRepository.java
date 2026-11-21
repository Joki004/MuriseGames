package com.murisegames.api.modules.Event.Scoring;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ScoreboardSnapshotRepository extends JpaRepository<ScoreboardSnapshot, UUID> {

    Optional<ScoreboardSnapshot> findByIdAndEventId(UUID id, UUID eventId);
}