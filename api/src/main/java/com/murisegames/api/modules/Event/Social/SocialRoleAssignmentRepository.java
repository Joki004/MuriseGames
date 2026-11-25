package com.murisegames.api.modules.Event.Social;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SocialRoleAssignmentRepository extends JpaRepository<SocialRoleAssignment, UUID> {

    Optional<SocialRoleAssignment> findByEventIdAndRoundNumberAndUserIdAndActiveTrue(
            UUID eventId, int roundNumber, UUID userId);

    List<SocialRoleAssignment> findByEventIdAndRoundNumberAndActiveTrue(
            UUID eventId, int roundNumber);
}