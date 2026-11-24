package com.murisegames.api.modules.Event.Alibi.Game;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AlibiPromptRepository extends JpaRepository<AlibiPrompt, UUID> {

    List<AlibiPrompt> findByRoundIdOrderByQuestionNumberAsc(UUID roundId);

    List<AlibiPrompt> findByRoundIdInOrderByQuestionNumberAsc(Collection<UUID> roundIds);

    Optional<AlibiPrompt> findByRoundIdAndQuestionNumber(UUID roundId, int questionNumber);

    Optional<AlibiPrompt> findByIdAndEventId(UUID id, UUID eventId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from AlibiPrompt p where p.id = :id and p.eventId = :eventId")
    Optional<AlibiPrompt> lock(@Param("eventId") UUID eventId, @Param("id") UUID id);
}
