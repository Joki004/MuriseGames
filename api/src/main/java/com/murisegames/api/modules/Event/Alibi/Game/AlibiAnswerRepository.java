package com.murisegames.api.modules.Event.Alibi.Game;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AlibiAnswerRepository extends JpaRepository<AlibiAnswer, UUID> {

    List<AlibiAnswer> findByPromptId(UUID promptId);

    List<AlibiAnswer> findByPromptIdIn(Collection<UUID> promptIds);

    Optional<AlibiAnswer> findByPromptIdAndSide(UUID promptId, String side);
}
