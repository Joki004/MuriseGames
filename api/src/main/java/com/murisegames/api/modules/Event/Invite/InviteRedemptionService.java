package com.murisegames.api.modules.Event.Invite;

import com.murisegames.api.modules.Event.Event;
import com.murisegames.api.modules.Event.EventRepository;
import com.murisegames.api.modules.Event.JoinRequest.JoinRequest;
import com.murisegames.api.modules.Event.JoinRequest.JoinRequestRepository;
import com.murisegames.api.modules.Event.Membership.EventMembership;
import com.murisegames.api.modules.Event.Membership.EventMembershipRepository;
import com.murisegames.api.modules.Event.Membership.EventRoleGrant;
import com.murisegames.api.modules.Event.Membership.EventRoleGrantRepository;
import com.murisegames.api.modules.Event.Participant.EventParticipant;
import com.murisegames.api.modules.Event.Participant.EventParticipantRepository;
import com.murisegames.api.modules.User.User;
import com.murisegames.api.modules.User.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InviteRedemptionService {

    private final EventInviteRepository inviteRepository;
    private final EventRepository eventRepository;
    private final EventMembershipRepository membershipRepository;
    private final EventRoleGrantRepository roleGrantRepository;
    private final JoinRequestRepository joinRequestRepository;
    private final EventParticipantRepository participantRepository;
    private final UserService userService;

    @Transactional
    public InviteRedeemResponse redeem(Jwt jwt, String token) {
        UUID userId = userService.extractUserId(jwt);
        String tokenHash = hashToken(token);

        // Scalar lookup: do not load a potentially stale invite entity
        // before acquiring the event lock.
        UUID eventId = inviteRepository.findEventIdByTokenHash(tokenHash)
                .orElseThrow(InviteRedemptionService::unavailable);

        Event event = eventRepository.lockById(eventId)
                .orElseThrow(InviteRedemptionService::unavailable);

        EventInvite invite = inviteRepository
                .lockForRedemption(eventId, tokenHash)
                .orElseThrow(InviteRedemptionService::unavailable);

        if (invite.getRevokedAt() != null
                || !invite.getExpiresAt().isAfter(Instant.now())
                || "completed".equals(event.getStatus())
                || "archived".equals(event.getStatus())) {
            throw unavailable();
        }

        var existingMembership =
                membershipRepository.findByEventIdAndUserId(eventId, userId);

        if (existingMembership.isPresent()) {
            EventMembership membership = existingMembership.get();

            if (!"active".equals(membership.getStatus())
                    || membershipRepository
                    .findActiveRoles(eventId, userId)
                    .isEmpty()) {
                throw unavailable();
            }

            // No role changes, participant changes or additional use.
            return InviteRedeemResponse.joined(eventId);
        }

        var pending = joinRequestRepository.findByEventIdAndUserIdAndStatus(
                eventId, userId, "pending"
        );

        if (pending.isPresent()) {
            // An existing request remains pending until organizer action.
            return InviteRedeemResponse.pending(pending.get().getId());
        }

        if (joinRequestRepository.existsByEventIdAndUserId(eventId, userId)) {
            // Do not bypass a previous decision with another invite.
            throw unavailable();
        }

        // Check capacity after checking for harmless repeats.
        if (invite.getMaxUses() != null
                && invite.getUseCount() >= invite.getMaxUses()) {
            throw unavailable();
        }

        User user = userService.resolveOrProvision(jwt);
        InviteRedeemResponse response;

        if ("private".equals(event.getVisibility())) {
            JoinRequest request = new JoinRequest();
            request.setEventId(eventId);
            request.setUserId(userId);
            request.setInviteId(invite.getId());
            request.setStatus("pending");

            joinRequestRepository.saveAndFlush(request);
            response = InviteRedeemResponse.pending(request.getId());

        } else if ("public".equals(event.getVisibility())) {
            EventMembership membership = new EventMembership();
            membership.setEventId(eventId);
            membership.setUserId(userId);
            membership.setStatus("active");
            membershipRepository.saveAndFlush(membership);

            EventRoleGrant playerGrant = new EventRoleGrant();
            playerGrant.setEventId(eventId);
            playerGrant.setMembershipId(membership.getId());
            playerGrant.setRole("PLAYER");

            // The redeeming account is the actor; the server's public-event
            // policy decides the role. No role is accepted from the client.
            playerGrant.setGrantedBy(userId);
            roleGrantRepository.saveAndFlush(playerGrant);

            EventParticipant participant = new EventParticipant();
            participant.setEventId(eventId);
            participant.setUserId(userId);
            participant.setDisplayName(user.getDisplayName());
            participant.setStatus("active");
            participant.setCaptain(false);
            participant.setTeamId(null);
            participantRepository.saveAndFlush(participant);

            response = InviteRedeemResponse.joined(eventId);
        } else {
            throw unavailable();
        }

        invite.setUseCount(Math.addExact(invite.getUseCount(), 1));
        inviteRepository.saveAndFlush(invite);

        return response;
    }

    private static ResponseStatusException unavailable() {
        return new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "This invitation cannot be used. Contact the organizer."
        );
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