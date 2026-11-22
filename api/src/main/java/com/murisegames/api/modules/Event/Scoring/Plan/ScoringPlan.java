package com.murisegames.api.modules.Event.Scoring.Plan;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "scoring_plans", schema = "murise")
@Getter
@Setter
@NoArgsConstructor
public class ScoringPlan {

    @Id
    @Column(name = "event_id", updatable = false)
    private UUID eventId;

    @Column(name = "schedule_id", nullable = false, updatable = false)
    private UUID scheduleId;

    @Column(name = "request_id", nullable = false, updatable = false)
    private UUID requestId;

    @Column(name = "request_hash", nullable = false, updatable = false)
    private String requestHash;

    @Column(name = "approved_by", nullable = false, updatable = false)
    private UUID approvedBy;

    @Column(name = "decision_note", nullable = false, updatable = false)
    private String decisionNote;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, updatable = false, columnDefinition = "jsonb")
    private Map<String, Object> snapshot;

    @Column(
            name = "created_at",
            nullable = false,
            insertable = false,
            updatable = false
    )
    private Instant createdAt;
}