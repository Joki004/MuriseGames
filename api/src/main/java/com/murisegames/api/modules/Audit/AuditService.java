package com.murisegames.api.modules.Audit;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditEventRepository auditEventRepository;

    @Transactional
    public void record(
            UUID eventId,
            UUID actorId,
            String action,
            String entityType,
            UUID entityId,
            Map<String, Object> details
    ) {
        AuditEvent entry = new AuditEvent();
        entry.setEventId(eventId);
        entry.setActorId(actorId);
        entry.setAction(action);
        entry.setEntityType(entityType);
        entry.setEntityId(entityId);
        entry.setDetails(details != null ? details : Map.of());
        auditEventRepository.saveAndFlush(entry);
    }
}