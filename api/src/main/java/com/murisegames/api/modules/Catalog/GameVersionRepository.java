package com.murisegames.api.modules.Catalog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GameVersionRepository extends JpaRepository<GameVersion, UUID> {

    Optional<GameVersion> findTopByGameDefinitionIdAndStatusOrderByVersionDesc(
            UUID gameDefinitionId,
            String status
    );

    Optional<GameVersion> findByIdAndGameDefinitionId(
            UUID id,
            UUID gameDefinitionId
    );

    List<GameVersion> findByGameDefinitionIdOrderByVersionDesc(UUID gameDefinitionId);
}