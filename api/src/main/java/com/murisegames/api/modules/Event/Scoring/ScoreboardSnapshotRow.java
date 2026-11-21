package com.murisegames.api.modules.Event.Scoring;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "scoreboard_snapshot_rows", schema = "murise")
@IdClass(ScoreboardSnapshotRowId.class)
@Getter
@Setter
@NoArgsConstructor
public class ScoreboardSnapshotRow {

    @Id
    @Column(name = "snapshot_id", nullable = false, updatable = false)
    private UUID snapshotId;

    @Id
    @Column(name = "team_id", nullable = false, updatable = false)
    private UUID teamId;

    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(nullable = false, updatable = false, precision = 12, scale = 4)
    private BigDecimal points;

    @Column(nullable = false, updatable = false)
    private int rank;
}