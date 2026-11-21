package com.murisegames.api.modules.Picture;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface PictureRepository extends JpaRepository<Picture, UUID> {

    Optional<Picture> findBySlotAndUserId(PictureSlot slot, UUID userId);

    Optional<Picture> findBySlotAndEventId(PictureSlot slot, UUID eventId);

    Optional<Picture> findBySlotAndTeamId(PictureSlot slot, UUID teamId);

    boolean existsByAssetId(UUID assetId);

    @Query(value = """
            select exists (
                select 1
                from murise.event_memberships actor
                join murise.event_memberships target
                  on target.event_id = actor.event_id
                where actor.user_id = :actorId
                  and target.user_id = :targetId
                  and actor.status = 'active'
                  and target.status = 'active'
                  and exists (
                      select 1
                      from murise.event_role_grants grant_row
                      where grant_row.event_id = actor.event_id
                        and grant_row.membership_id = actor.id
                        and grant_row.revoked_at is null
                  )
                  and exists (
                      select 1
                      from murise.event_role_grants grant_row
                      where grant_row.event_id = target.event_id
                        and grant_row.membership_id = target.id
                        and grant_row.revoked_at is null
                  )
            )
            """, nativeQuery = true)
    boolean sharesActiveEvent(
            @Param("actorId") UUID actorId,
            @Param("targetId") UUID targetId
    );
}