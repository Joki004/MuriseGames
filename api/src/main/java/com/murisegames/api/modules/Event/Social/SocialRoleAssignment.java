package com.murisegames.api.modules.Event.Social;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "social_role_assignments", schema = "murise")
@Getter
@Setter
@NoArgsConstructor
public class SocialRoleAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "fixture_id")
    private UUID fixtureId;

    @Column(name = "round_number", nullable = false)
    private int roundNumber;

    @Column(name = "case_id", nullable = false)
    private UUID caseId;

    @Column(name = "case_version_id", nullable = false)
    private UUID caseVersionId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "team_id")
    private UUID teamId;

    @Column(nullable = false, columnDefinition = "text")
    private String role;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "assigned_by", nullable = false, updatable = false)
    private UUID assignedBy;

    @Column(name = "assigned_at", nullable = false, insertable = false, updatable = false)
    private Instant assignedAt;
}