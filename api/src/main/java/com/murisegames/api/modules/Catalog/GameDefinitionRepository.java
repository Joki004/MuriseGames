package com.murisegames.api.modules.Catalog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GameDefinitionRepository extends JpaRepository<GameDefinition, UUID> {

    List<GameDefinition> findByActiveTrueOrderByNameAsc();

    Optional<GameDefinition> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);
}