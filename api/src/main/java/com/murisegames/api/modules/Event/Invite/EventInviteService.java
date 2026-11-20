package com.murisegames.api.modules.Event.Invite;

import com.murisegames.api.modules.Event.Event;
import com.murisegames.api.modules.Event.EventRepository;
import com.murisegames.api.modules.Event.Invite.dto.InviteCreateRequest;
import com.murisegames.api.modules.Event.Membership.EventMembershipService;
import com.murisegames.api.modules.User.UserService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EventInviteService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final EventInviteRepository inviteRepository;
    private final EventRepository eventRepository;
    private final EventMembershipService membershipService;
    private final UserService userService;
    private final EntityManager entityManager;

    @Transactional(readOnly = true)
    public List<EventInviteResponse> list(Jwt jwt, UUID eventId) {
        UUID userId = userService.extractUserId(jwt);
        membershipService.requireOrganizer(eventId, userId);

        return inviteRepository
                .findByEventIdOrderByCreatedAtDescIdDesc(eventId)
                .stream()
                .map(EventInviteResponse::from)
                .toList();
    }

    @Transactional
    public InviteCreatedResponse create(
            Jwt jwt,
            UUID eventId,
            InviteCreateRequest request
    ) {
        UUID userId = userService.extractUserId(jwt);
        Event event = lockForOrganizer(eventId, userId);

        if ("completed".equals(event.getStatus())
                || "archived".equals(event.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Invites cannot be created for a closed event."
            );
        }

        byte[] randomBytes = new byte[32];
        RANDOM.nextBytes(randomBytes);

        String token = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(randomBytes);

        EventInvite invite = new EventInvite();
        invite.setEventId(eventId);
        invite.setTokenHash(hashToken(token));
        invite.setExpiresAt(
                Instant.now().plus(request.expiresInHours(), ChronoUnit.HOURS)
        );
        invite.setMaxUses(request.maxUses());
        invite.setUseCount(0);
        invite.setCreatedBy(userId);

        inviteRepository.saveAndFlush(invite);
        entityManager.refresh(invite);

        // Never log this response or persist the original token.
        return new InviteCreatedResponse(
                EventInviteResponse.from(invite),
                token
        );
    }

    @Transactional
    public EventInviteResponse revoke(
            Jwt jwt,
            UUID eventId,
            UUID inviteId
    ) {
        UUID userId = userService.extractUserId(jwt);
        lockForOrganizer(eventId, userId);

        EventInvite invite = inviteRepository
                .findByIdAndEventId(inviteId, eventId)
                .orElseThrow(() ->
                        new EntityNotFoundException("Invite not found.")
                );

        if (invite.getRevokedAt() == null) {
            invite.setRevokedAt(Instant.now());
            inviteRepository.saveAndFlush(invite);
        }

        return EventInviteResponse.from(invite);
    }

    private Event lockForOrganizer(UUID eventId, UUID userId) {
        membershipService.requireOrganizer(eventId, userId);

        Event event = eventRepository.lockById(eventId)
                .orElseThrow(() ->
                        new EntityNotFoundException("Event not found.")
                );

        membershipService.requireOrganizer(eventId, userId);
        return event;
    }

    private static String hashToken(String token) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));

            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }
}