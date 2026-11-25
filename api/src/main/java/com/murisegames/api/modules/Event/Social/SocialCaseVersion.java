package com.murisegames.api.modules.Event.Social;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Immutable: the database rejects UPDATE and DELETE on social_case_versions. */
@Entity
@Table(name = "social_case_versions", schema = "murise")
@Getter
@Setter
@NoArgsConstructor
public class SocialCaseVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "case_id", nullable = false, updatable = false)
    private UUID caseId;

    @Column(name = "version_number", nullable = false, updatable = false)
    private int versionNumber;

    @Column(name = "public_setup", nullable = false, updatable = false, columnDefinition = "text")
    private String publicSetup;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "defender_goals", nullable = false, updatable = false, columnDefinition = "jsonb")
    private List<String> defenderGoals;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "defender_constraints", nullable = false, updatable = false, columnDefinition = "jsonb")
    private List<String> defenderConstraints;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "attacker_red_flags", nullable = false, updatable = false, columnDefinition = "jsonb")
    private List<String> attackerRedFlags;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "attacker_bait", nullable = false, updatable = false, columnDefinition = "jsonb")
    private List<String> attackerBait;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "judge_rubric", nullable = false, updatable = false, columnDefinition = "jsonb")
    private Map<String, Object> judgeRubric;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;
}