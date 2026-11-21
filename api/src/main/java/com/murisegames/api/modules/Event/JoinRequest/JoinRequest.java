package com.murisegames.api.modules.Event.JoinRequest;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "join_requests", schema = "murise")
@Getter
@Setter
@NoArgsConstructor
public class JoinRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "invite_id", updatable = false)
    private UUID inviteId;

    @Column(nullable = false, columnDefinition = "text")
    private String status;

    @Column(name = "decided_by")
    private UUID decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(
            name = "created_at",
            nullable = false,
            insertable = false,
            updatable = false
    )
    private Instant createdAt;

    @Column(
            name = "updated_at",
            nullable = false,
            insertable = false,
            updatable = false
    )
    private Instant updatedAt;
}