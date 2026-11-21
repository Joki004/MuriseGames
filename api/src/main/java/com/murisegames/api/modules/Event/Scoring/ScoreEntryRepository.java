package com.murisegames.api.modules.Event.Scoring;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ScoreEntryRepository extends JpaRepository<ScoreEntry, UUID> {

    List<ScoreEntry> findByEventIdOrderByCreatedAtDesc(UUID eventId);

    Optional<ScoreEntry> findByIdAndEventId(UUID id, UUID eventId);

    boolean existsByReversesEntryId(UUID reversesEntryId);

    boolean existsByEventIdAndSourceKey(UUID eventId, String sourceKey);

    interface TeamPointTotal {
        UUID getTeamId();
        BigDecimal getTotalPoints();
    }

    @Query("""
            select se.teamId as teamId, coalesce(sum(se.delta), 0) as totalPoints
            from ScoreEntry se
            where se.eventId = :eventId
            group by se.teamId
            """)
    List<TeamPointTotal> sumPointsByTeam(@Param("eventId") UUID eventId);
}