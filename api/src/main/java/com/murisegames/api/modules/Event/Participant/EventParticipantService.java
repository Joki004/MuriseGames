package com.murisegames.api.modules.Event.Participant;

import com.murisegames.api.modules.Event.Membership.EventMembershipService;
import com.murisegames.api.modules.Event.Team.Team;
import com.murisegames.api.modules.Event.Team.TeamRepository;
import com.murisegames.api.modules.User.User;
import com.murisegames.api.modules.User.UserService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EventParticipantService {

    public static final String STATUS_ACTIVE = "active";

    private final EventParticipantRepository participantRepository;
    private final TeamRepository teamRepository;
    private final EventMembershipService membershipService;
    private final UserService userService;
    private final EntityManager entityManager;

    @Transactional(readOnly = true)
    public List<EventParticipantResponse> listParticipants(Jwt jwt, UUID eventId) {
        UUID actorId = userService.extractUserId(jwt);
        membershipService.requireAccess(eventId, actorId);

        return participantRepository.findByEventIdOrderByCreatedAtAsc(eventId)
                .stream()
                .map(EventParticipantResponse::from)
                .toList();
    }

    @Transactional
    public EventParticipantResponse assignTeam(
            Jwt jwt,
            UUID eventId,
            UUID participantId,
            UUID targetTeamId
    ) {
        UUID actorId = userService.extractUserId(jwt);
        membershipService.requireOrganizer(eventId, actorId);

        EventParticipant participant = participantRepository
                .findByIdAndEventId(participantId, eventId)
                .orElseThrow(() -> new EntityNotFoundException("Participant not found."));

        if (Objects.equals(participant.getTeamId(), targetTeamId)) {
            return EventParticipantResponse.from(participant);
        }

        // Block moving a player off a team that has already started play.
        if (participant.getTeamId() != null
                && teamRepository.hasPlayHistory(eventId, participant.getTeamId())) {
            throw new IllegalStateException(
                    "Cannot reassign a participant after their team has started play."
            );
        }

        if (targetTeamId != null) {
            Team targetTeam = teamRepository.findByIdAndEventId(targetTeamId, eventId)
                    .orElseThrow(() -> new EntityNotFoundException("Team not found."));

            if (!targetTeam.isActive()) {
                throw new IllegalStateException("Cannot assign a participant to an inactive team.");
            }
        }

        participant.setTeamId(targetTeamId);
        participantRepository.saveAndFlush(participant);
        entityManager.refresh(participant);

        return EventParticipantResponse.from(participant);
    }

    /**
     * Internal helper used when approving a join request.
     * Creates or reactivates the participant row and optionally assigns their initial team.
     */
    @Transactional
    public EventParticipant ensureActiveParticipant(
            UUID eventId,
            UUID userId,
            UUID teamId
    ) {
        if (teamId != null) {
            Team targetTeam = teamRepository.findByIdAndEventId(teamId, eventId)
                    .orElseThrow(() -> new EntityNotFoundException("Team not found."));

            if (!targetTeam.isActive()) {
                throw new IllegalStateException("Cannot assign a participant to an inactive team.");
            }
        }

        EventParticipant participant = participantRepository
                .findByEventIdAndUserId(eventId, userId)
                .orElseGet(() -> {
                    User user = userService.getRequiredUser(userId);
                    EventParticipant created = new EventParticipant();
                    created.setEventId(eventId);
                    created.setUserId(userId);
                    created.setDisplayName(user.getDisplayName());
                    created.setCaptain(false);
                    return created;
                });

        participant.setStatus(STATUS_ACTIVE);
        if (teamId != null) {
            participant.setTeamId(teamId);
        }

        participantRepository.saveAndFlush(participant);
        entityManager.refresh(participant);
        return participant;
    }
}