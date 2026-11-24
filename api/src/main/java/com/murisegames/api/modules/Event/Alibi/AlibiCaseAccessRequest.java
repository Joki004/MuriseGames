package com.murisegames.api.modules.Event.Alibi;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** A request to open a case someone did not write; the event creator decides. */
@Entity
@Table(name = "alibi_case_access_requests", schema = "murise")
@Getter
@Setter
@NoArgsConstructor
public class AlibiCaseAccessRequest {

    public static final String PENDING = "pending";
    public static final String APPROVED = "approved";
    public static final String REFUSED = "refused";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "case_id", nullable = false, updatable = false)
    private UUID caseId;

    @Column(name = "requester_id", nullable = false, updatable = false)
    private UUID requesterId;

    @Column(nullable = false, updatable = false, columnDefinition = "text")
    private String reason;

    @Column(nullable = false, columnDefinition = "text")
    private String status;

    @Column(name = "decided_by")
    private UUID decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decision_note", columnDefinition = "text")
    private String decisionNote;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;
}
