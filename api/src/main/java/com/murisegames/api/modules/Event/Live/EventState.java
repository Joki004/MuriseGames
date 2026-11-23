package com.murisegames.api.modules.Event.Live;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "event_state", schema = "murise")
@Getter
@Setter
@NoArgsConstructor
public class EventState {

    @Id
    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "current_event_game_id")
    private UUID currentEventGameId;

    @Column(name = "current_window_start_at")
    private Instant currentWindowStartAt;

    @Column(nullable = false, columnDefinition = "text")
    private String phase;

    @Column(name = "deadline_at")
    private Instant deadlineAt;

    @Column(name = "paused_remaining_ms")
    private Long pausedRemainingMs;

    @Column(nullable = false)
    private long version = 0;

    @Column(
            name = "updated_at",
            nullable = false,
            insertable = false,
            updatable = false
    )
    private Instant updatedAt;
}