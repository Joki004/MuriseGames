package com.murisegames.api.modules.Event.Alibi.Game;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "alibi_rounds", schema = "murise")
@Getter
@Setter
@NoArgsConstructor
public class AlibiRound {

    public static final String SETUP = "setup";
    public static final String PREPARING = "preparing";
    public static final String QUESTIONING = "questioning";
    public static final String JUDGING = "judging";
    public static final String DONE = "done";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "session_id", nullable = false, updatable = false)
    private UUID sessionId;

    @Column(name = "pair_number", nullable = false, updatable = false)
    private int pairNumber;

    @Column(name = "player_a")
    private UUID playerA;

    @Column(name = "player_b")
    private UUID playerB;

    @Column(name = "case_id")
    private UUID caseId;

    @Column(name = "case_version_id")
    private UUID caseVersionId;

    @Column(nullable = false, columnDefinition = "text")
    private String status;

    @Column(name = "prep_ends_at")
    private Instant prepEndsAt;

    @Column(name = "current_question", nullable = false)
    private int currentQuestion;

    @Column(name = "question_count", nullable = false)
    private int questionCount;

    @Column(name = "revealed_count", nullable = false)
    private int revealedCount;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "started_by")
    private UUID startedBy;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    public String sideOf(UUID userId) {
        if (userId == null) {
            return null;
        }
        if (userId.equals(playerA)) {
            return "A";
        }
        if (userId.equals(playerB)) {
            return "B";
        }
        return null;
    }
}
