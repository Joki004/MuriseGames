package com.murisegames.api.modules.Catalog;

import com.murisegames.api.modules.Catalog.dto.CatalogGameCreateRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CatalogService {

    private final GameDefinitionRepository definitionRepository;
    private final GameVersionRepository versionRepository;

    @Transactional(readOnly = true)
    public List<CatalogGameResponse> listPublishedGames() {
        return definitionRepository.findByActiveTrueOrderByNameAsc()
                .stream()
                .flatMap(def -> versionRepository
                        .findTopByGameDefinitionIdAndStatusOrderByVersionDesc(def.getId(), "published")
                        .stream()
                        .map(ver -> CatalogGameResponse.from(def, ver)))
                .toList();
    }

    @Transactional
    public CatalogGameResponse createAndPublish(CatalogGameCreateRequest request) {
        String normalizedCode = request.code().trim().toLowerCase();
        if (definitionRepository.existsByCodeIgnoreCase(normalizedCode)) {
            throw new IllegalStateException("A game with code '" + normalizedCode + "' already exists.");
        }

        GameDefinition definition = new GameDefinition();
        definition.setCode(normalizedCode);
        definition.setName(request.name().trim());
        definition.setKind(request.kind().trim());
        definition.setActive(true);
        definitionRepository.saveAndFlush(definition);

        GameVersion version = new GameVersion();
        version.setGameDefinitionId(definition.getId());
        version.setVersion(1);
        version.setSummary(request.summary().trim());
        version.setRulesMarkdown(request.rulesMarkdown().trim());
        version.setEndGameScoringSummary(request.endGameScoringSummary().trim());
        version.setParticipationMode(request.participationMode().trim());
        version.setScoringHandler(request.scoringHandler().trim());
        version.setScoringConfig(request.scoringConfig() != null
                ? new HashMap<>(request.scoringConfig())
                : new HashMap<>());
        version.setSettingsSchema(new HashMap<>());
        version.setResultSchema(new HashMap<>());
        version.setStatus("published");
        versionRepository.saveAndFlush(version);

        return CatalogGameResponse.from(definition, version);
    }
}