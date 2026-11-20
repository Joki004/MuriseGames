package com.murisegames.api.modules.Event;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "events", schema = "murise")
@Getter
@Setter
@NoArgsConstructor
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, columnDefinition = "text")
    private String name;

    @Column(columnDefinition = "text")
    private String theme;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(nullable = false, columnDefinition = "text")
    private String timezone;

    @Column(columnDefinition = "text")
    private String venue;

    @Column(nullable = false, columnDefinition = "text")
    private String status;

    @Column(nullable = false, columnDefinition = "text")
    private String visibility;

    @Column(
            name = "scoreboard_visibility",
            nullable = false,
            columnDefinition = "text"
    )
    private String scoreboardVisibility;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

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