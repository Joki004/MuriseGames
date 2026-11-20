package com.murisegames.api.modules.Event.Invite;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EventInviteRepository
        extends JpaRepository<EventInvite, UUID> {

    List<EventInvite> findByEventIdOrderByCreatedAtDescIdDesc(UUID eventId);

    Optional<EventInvite> findByIdAndEventId(UUID id, UUID eventId);
}