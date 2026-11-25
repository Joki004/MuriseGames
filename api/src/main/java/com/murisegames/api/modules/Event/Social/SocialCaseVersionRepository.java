package com.murisegames.api.modules.Event.Social;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SocialCaseVersionRepository extends JpaRepository<SocialCaseVersion, UUID> {

    Optional<SocialCaseVersion> findByCaseIdAndVersionNumber(UUID caseId, int versionNumber);

    List<SocialCaseVersion> findByCaseIdOrderByVersionNumberDesc(UUID caseId);

    List<SocialCaseVersion> findByEventId(UUID eventId);
}