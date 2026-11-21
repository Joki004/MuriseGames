package com.murisegames.api.modules.Event.EventGame;

import com.murisegames.api.modules.Catalog.GameDefinition;
import com.murisegames.api.modules.Catalog.GameVersion;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record EventGameResponse(
        UUID id,
        UUID eventId,
        UUID gameDefinitionId,
        UUID gameVersionId,
        String code,
        String name,
        String kind,
        String summary,
        String participationMode,
        String status,
        int sortOrder,
        String houseRulesNote,
        String scoreboardVisibilityOverride,
        Map<String, Object> settingsSnapshot,
        Instant createdAt,
        Instant updatedAt,
        Integer rulesVersion,
        String rulesMarkdown,
        String endGameScoringSummary
) {

    public static EventGameResponse from(
            EventGame eg,
            GameDefinition def,
            GameVersion ver
    ) {
        return new EventGameResponse(
                eg.getId(),
                eg.getEventId(),
                eg.getGameDefinitionId(),
                eg.getGameVersionId(),
                def != null ? def.getCode() : null,
                def != null ? def.getName() : "Unknown Game",
                def != null ? def.getKind() : null,
                ver != null ? ver.getSummary() : null,
                ver != null ? ver.getParticipationMode() : null,
                eg.getStatus(),
                eg.getSortOrder(),
                eg.getHouseRulesNote(),
                eg.getScoreboardVisibilityOverride(),
                eg.getSettingsSnapshot(),
                eg.getCreatedAt(),
                eg.getUpdatedAt(),
                ver != null && "published".equals(ver.getStatus())
                        ? ver.getVersion() : null,
                ver != null && "published".equals(ver.getStatus())
                        ? ver.getRulesMarkdown() : null,
                ver != null && "published".equals(ver.getStatus())
                        ? ver.getEndGameScoringSummary() : null
        );
    }
}