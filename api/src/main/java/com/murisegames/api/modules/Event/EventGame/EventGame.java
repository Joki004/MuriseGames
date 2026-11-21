package com.murisegames.api.modules.Event.EventGame;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "event_games", schema = "murise")
@Getter
@Setter
@NoArgsConstructor
public class EventGame {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "game_definition_id", nullable = false, updatable = false)
    private UUID gameDefinitionId;

    @Column(name = "game_version_id", nullable = false, updatable = false)
    private UUID gameVersionId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "settings_snapshot", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> settingsSnapshot = new HashMap<>();

    @Column(name = "scoreboard_visibility_override", columnDefinition = "text")
    private String scoreboardVisibilityOverride;

    @Column(name = "house_rules_note", columnDefinition = "text")
    private String houseRulesNote;

    @Column(nullable = false, columnDefinition = "text")
    private String status;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;
}