package com.murisegames.api.modules.Event.Team;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TeamRepository extends JpaRepository<Team, UUID> {

    List<Team> findByEventIdAndActiveTrueOrderBySortOrderAscNameAsc(
            UUID eventId
    );

    Optional<Team> findByIdAndEventId(UUID id, UUID eventId);

    @Query(value = """
            select exists (
                select 1
                from murise.schedule_versions
                where event_id = :eventId
                  and status = 'published'
            ) or exists (
                select 1
                from murise.fixtures f
                where f.event_id = :eventId
                  and f.status not in ('scheduled', 'voided')
            )
            """, nativeQuery = true)
    boolean hasPublishedScheduleOrPlay(@Param("eventId") UUID eventId);

    @Query(value = """
            select exists (
                select 1
                from murise.fixture_teams ft
                join murise.fixtures f
                  on f.event_id = ft.event_id
                 and f.id = ft.fixture_id
                where ft.event_id = :eventId
                  and ft.team_id = :teamId
                  and (
                      f.status <> 'scheduled'
                      or exists (
                          select 1
                          from murise.rounds r
                          where r.event_id = f.event_id
                            and r.fixture_id = f.id
                            and r.status <> 'scheduled'
                      )
                  )
            )
            """, nativeQuery = true)
    boolean hasPlayHistory(
            @Param("eventId") UUID eventId,
            @Param("teamId") UUID teamId
    );
}