package com.murisegames.api.modules.Event.Alibi;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AlibiCaseVersionRepository extends JpaRepository<AlibiCaseVersion, UUID> {

    Optional<AlibiCaseVersion> findByCaseIdAndVersionNumber(UUID caseId, int versionNumber);

    List<AlibiCaseVersion> findByCaseIdOrderByVersionNumberDesc(UUID caseId);

    List<AlibiCaseVersion> findByEventId(UUID eventId);
}
