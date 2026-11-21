package com.murisegames.api.modules.Catalog;

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
@Table(name = "game_versions", schema = "murise")
@Getter
@Setter
@NoArgsConstructor
public class GameVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "game_definition_id", nullable = false, updatable = false)
    private UUID gameDefinitionId;

    @Column(nullable = false, updatable = false)
    private int version;

    @Column(nullable = false, columnDefinition = "text")
    private String summary;

    @Column(name = "rules_markdown", nullable = false, columnDefinition = "text")
    private String rulesMarkdown;

    @Column(name = "end_game_scoring_summary", nullable = false, columnDefinition = "text")
    private String endGameScoringSummary;

    @Column(name = "participation_mode", nullable = false, columnDefinition = "text")
    private String participationMode;

    @Column(name = "scoring_handler", nullable = false, columnDefinition = "text")
    private String scoringHandler;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "scoring_config", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> scoringConfig = new HashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "settings_schema", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> settingsSchema = new HashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result_schema", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> resultSchema = new HashMap<>();

    @Column(nullable = false, columnDefinition = "text")
    private String status;

    @Column(name = "source_ref", columnDefinition = "text")
    private String sourceRef;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;
}