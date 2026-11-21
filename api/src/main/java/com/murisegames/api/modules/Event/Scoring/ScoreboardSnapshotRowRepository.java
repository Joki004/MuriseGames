package com.murisegames.api.modules.Event.Scoring;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ScoreboardSnapshotRowRepository
        extends JpaRepository<ScoreboardSnapshotRow, ScoreboardSnapshotRowId> {

    List<ScoreboardSnapshotRow> findBySnapshotIdOrderByRankAsc(UUID snapshotId);
}