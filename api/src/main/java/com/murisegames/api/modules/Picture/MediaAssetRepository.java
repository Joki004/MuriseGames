package com.murisegames.api.modules.Picture;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MediaAssetRepository extends JpaRepository<MediaAsset, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select asset from MediaAsset asset where asset.id = :id")
    Optional<MediaAsset> lockById(@Param("id") UUID id);

    @Query(value = """
            select id
            from murise.media_assets
            where purpose = 'picture'
              and (
                  status in ('failed', 'delete_pending')
                  or (status = 'pending' and created_at < :staleBefore)
              )
            order by created_at
            limit 50
            """, nativeQuery = true)
    List<UUID> findCleanupCandidates(
            @Param("staleBefore") Instant staleBefore
    );
}