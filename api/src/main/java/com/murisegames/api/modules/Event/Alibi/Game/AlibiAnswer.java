package com.murisegames.api.modules.Event.Alibi.Game;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** Immutable: the table rejects UPDATE and DELETE; UNIQUE(prompt_id, side). */
@Entity
@Table(name = "alibi_answers", schema = "murise")
@Getter
@Setter
@NoArgsConstructor
public class AlibiAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "prompt_id", nullable = false, updatable = false)
    private UUID promptId;

    @Column(nullable = false, updatable = false, columnDefinition = "text")
    private String side;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "answer_text", updatable = false, columnDefinition = "text")
    private String answerText;

    @Column(nullable = false, updatable = false)
    private boolean missing;

    @Column(name = "away_ms", nullable = false, updatable = false)
    private int awayMs;

    @Column(name = "away_count", nullable = false, updatable = false)
    private int awayCount;

    @Column(name = "locked_at", nullable = false, insertable = false, updatable = false)
    private Instant lockedAt;
}
