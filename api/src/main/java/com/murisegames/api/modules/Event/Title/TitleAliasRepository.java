package com.murisegames.api.modules.Event.Title;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TitleAliasRepository extends JpaRepository<TitleAlias, UUID> {

    List<TitleAlias> findByEventId(UUID eventId);

    void deleteBySceneId(UUID sceneId);
}