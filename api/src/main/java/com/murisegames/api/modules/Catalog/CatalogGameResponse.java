package com.murisegames.api.modules.Catalog;

import java.util.Map;
import java.util.UUID;

public record CatalogGameResponse(
        UUID definitionId,
        UUID latestVersionId,
        int version,
        String code,
        String name,
        String kind,
        String summary,
        String rulesMarkdown,
        String endGameScoringSummary,
        String participationMode,
        String scoringHandler,
        Map<String, Object> scoringConfig
) {

    public static CatalogGameResponse from(GameDefinition def, GameVersion ver) {
        return new CatalogGameResponse(
                def.getId(),
                ver.getId(),
                ver.getVersion(),
                def.getCode(),
                def.getName(),
                def.getKind(),
                ver.getSummary(),
                ver.getRulesMarkdown(),
                ver.getEndGameScoringSummary(),
                ver.getParticipationMode(),
                ver.getScoringHandler(),
                ver.getScoringConfig()
        );
    }
}