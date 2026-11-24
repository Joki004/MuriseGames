package com.murisegames.api.modules.Event.Alibi.Game;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** One team's Alibi slot: their fixture, played as two rounds (one per pair). */
@Entity
@Table(name = "alibi_sessions", schema = "murise")
@Getter
@Setter
@NoArgsConstructor
public class AlibiSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "event_game_id", nullable = false, updatable = false)
    private UUID eventGameId;

    @Column(name = "fixture_id", nullable = false, updatable = false)
    private UUID fixtureId;

    @Column(name = "team_id", nullable = false, updatable = false)
    private UUID teamId;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "recorded_at")
    private Instant recordedAt;

    @Column(name = "recorded_by")
    private UUID recordedBy;
}
