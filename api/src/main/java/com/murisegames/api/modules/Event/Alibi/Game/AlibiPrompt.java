package com.murisegames.api.modules.Event.Alibi.Game;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** One question of a round, shared by both sides of the pair. */
@Entity
@Table(name = "alibi_prompts", schema = "murise")
@Getter
@Setter
@NoArgsConstructor
public class AlibiPrompt {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "round_id", nullable = false, updatable = false)
    private UUID roundId;

    @Column(name = "question_number", nullable = false, updatable = false)
    private int questionNumber;

    @Column(name = "question_text", nullable = false, updatable = false, columnDefinition = "text")
    private String questionText;

    @Column(name = "opened_at", nullable = false, updatable = false)
    private Instant openedAt;

    @Column(name = "closes_at", nullable = false)
    private Instant closesAt;

    @Column(name = "compare_ready_at")
    private Instant compareReadyAt;

    private Boolean verdict;

    @Column(name = "verdict_source", columnDefinition = "text")
    private String verdictSource;

    @Column(name = "decided_by")
    private UUID decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "revealed_at")
    private Instant revealedAt;
}
