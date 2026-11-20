package com.murisegames.api.modules.Event.Membership;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface EventRoleGrantRepository
        extends JpaRepository<EventRoleGrant, UUID> {
}