package com.murisegames.api.modules.Event.Scoring;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "score_entries", schema = "murise")
@Getter
@Setter
@NoArgsConstructor
public class ScoreEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "team_id", nullable = false, updatable = false)
    private UUID teamId;

    @Column(name = "result_id", updatable = false)
    private UUID resultId;

    @Column(nullable = false, updatable = false, columnDefinition = "text")
    private String category;

    @Column(nullable = false, updatable = false, precision = 12, scale = 4)
    private BigDecimal delta;

    @Column(name = "source_key", nullable = false, updatable = false, columnDefinition = "text")
    private String sourceKey;

    @Column(nullable = false, updatable = false, columnDefinition = "text")
    private String reason;

    @Column(name = "actor_id", nullable = false, updatable = false)
    private UUID actorId;

    @Column(name = "reverses_entry_id", updatable = false)
    private UUID reversesEntryId;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;
}