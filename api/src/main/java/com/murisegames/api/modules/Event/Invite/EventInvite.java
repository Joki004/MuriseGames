package com.murisegames.api.modules.Event.Invite;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "event_invites", schema = "murise")
@Getter
@Setter
@NoArgsConstructor
public class EventInvite {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(
            name = "token_hash",
            nullable = false,
            updatable = false,
            columnDefinition = "text"
    )
    private String tokenHash;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "max_uses", updatable = false)
    private Integer maxUses;

    @Column(name = "use_count", nullable = false)
    private int useCount;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @Column(
            name = "created_at",
            nullable = false,
            insertable = false,
            updatable = false
    )
    private Instant createdAt;
}