package com.murisegames.api.modules.User;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PlatformRoleGrantRepository extends JpaRepository<PlatformRoleGrant, UUID> {

    List<PlatformRoleGrant> findByUserIdAndRevokedAtIsNull(UUID userId);

    boolean existsByUserIdAndRoleAndRevokedAtIsNull(UUID userId, PlatformRole role);
}