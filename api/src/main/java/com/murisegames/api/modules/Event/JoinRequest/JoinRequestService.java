package com.murisegames.api.modules.Event.JoinRequest;

import com.murisegames.api.modules.Event.Membership.EventMembershipService;
import com.murisegames.api.modules.Event.Participant.EventParticipantService;
import com.murisegames.api.modules.User.User;
import com.murisegames.api.modules.User.UserRepository;
import com.murisegames.api.modules.User.UserService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class JoinRequestService {

    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_APPROVED = "approved";
    public static final String STATUS_REJECTED = "rejected";

    private final JoinRequestRepository joinRequestRepository;
    private final EventMembershipService membershipService;
    private final EventParticipantService participantService;
    private final UserService userService;
    private final UserRepository userRepository;
    private final EntityManager entityManager;

    @Transactional(readOnly = true)
    public List<JoinRequestResponse> listRequests(
            Jwt jwt,
            UUID eventId,
            String statusFilter
    ) {
        UUID actorId = userService.extractUserId(jwt);
        membershipService.requireOrganizer(eventId, actorId);

        List<JoinRequest> requests = (statusFilter != null && !statusFilter.isBlank())
                ? joinRequestRepository.findByEventIdAndStatusOrderByCreatedAtAsc(
                eventId,
                statusFilter.trim().toLowerCase()
        )
                : joinRequestRepository.findByEventIdOrderByCreatedAtDesc(eventId);

        Set<UUID> userIds = requests.stream()
                .map(JoinRequest::getUserId)
                .collect(Collectors.toSet());

        Map<UUID, String> displayNames = userRepository.findAllById(userIds)
                .stream()
                .collect(Collectors.toMap(User::getId, User::getDisplayName));

        return requests.stream()
                .map(req -> JoinRequestResponse.from(
                        req,
                        displayNames.getOrDefault(req.getUserId(), "Unknown Player")
                ))
                .toList();
    }

    @Transactional
    public JoinRequestResponse approve(
            Jwt jwt,
            UUID eventId,
            UUID requestId,
            UUID teamId
    ) {
        UUID actorId = userService.extractUserId(jwt);
        membershipService.requireOrganizer(eventId, actorId);

        JoinRequest request = joinRequestRepository
                .findByIdAndEventIdForUpdate(requestId, eventId)
                .orElseThrow(() -> new EntityNotFoundException("Join request not found."));

        if (STATUS_REJECTED.equalsIgnoreCase(request.getStatus())) {
            throw new IllegalStateException("Cannot approve a join request that was already rejected.");
        }

        // Idempotent: if already approved, ensure participant/team state and return.
        if (STATUS_APPROVED.equalsIgnoreCase(request.getStatus())) {
            if (teamId != null) {
                participantService.ensureActiveParticipant(eventId, request.getUserId(), teamId);
            }
            return toResponse(request);
        }

        // Grant ACTIVE membership + PLAYER role only (never ORGANIZER).
        membershipService.grantPlayerMembership(eventId, request.getUserId(), actorId);
        participantService.ensureActiveParticipant(eventId, request.getUserId(), teamId);

        request.setStatus(STATUS_APPROVED);
        request.setDecidedBy(actorId);
        request.setDecidedAt(Instant.now());

        joinRequestRepository.saveAndFlush(request);
        entityManager.refresh(request);

        return toResponse(request);
    }

    @Transactional
    public JoinRequestResponse reject(Jwt jwt, UUID eventId, UUID requestId) {
        UUID actorId = userService.extractUserId(jwt);
        membershipService.requireOrganizer(eventId, actorId);

        JoinRequest request = joinRequestRepository
                .findByIdAndEventIdForUpdate(requestId, eventId)
                .orElseThrow(() -> new EntityNotFoundException("Join request not found."));

        if (STATUS_APPROVED.equalsIgnoreCase(request.getStatus())) {
            throw new IllegalStateException("Cannot reject a join request that was already approved.");
        }

        if (STATUS_REJECTED.equalsIgnoreCase(request.getStatus())) {
            return toResponse(request);
        }

        request.setStatus(STATUS_REJECTED);
        request.setDecidedBy(actorId);
        request.setDecidedAt(Instant.now());

        joinRequestRepository.saveAndFlush(request);
        entityManager.refresh(request);

        return toResponse(request);
    }

    private JoinRequestResponse toResponse(JoinRequest request) {
        String displayName = userRepository.findById(request.getUserId())
                .map(User::getDisplayName)
                .orElse("Unknown Player");
        return JoinRequestResponse.from(request, displayName);
    }
}