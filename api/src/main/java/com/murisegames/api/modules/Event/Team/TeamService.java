package com.murisegames.api.modules.Event.Team;

import com.murisegames.api.modules.Event.Event;
import com.murisegames.api.modules.Event.EventRepository;
import com.murisegames.api.modules.Event.Membership.EventMembershipService;
import com.murisegames.api.modules.Event.Team.dto.TeamSaveRequest;
import com.murisegames.api.modules.User.UserService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TeamService {

    private final TeamRepository teamRepository;
    private final EventRepository eventRepository;
    private final EventMembershipService membershipService;
    private final UserService userService;
    private final EntityManager entityManager;

    @Transactional(readOnly = true)
    public TeamListResponse list(Jwt jwt, UUID eventId) {
        UUID userId = userService.extractUserId(jwt);
        Set<String> roles = membershipService.requireAccess(eventId, userId);

        List<TeamResponse> teams = teamRepository
                .findByEventIdAndActiveTrueOrderBySortOrderAscNameAsc(eventId)
                .stream()
                .map(TeamResponse::from)
                .toList();

        return new TeamListResponse(teams, roles.contains("ORGANIZER"));
    }

    @Transactional
    public TeamResponse create(
            Jwt jwt,
            UUID eventId,
            TeamSaveRequest request
    ) {
        lockForManagement(jwt, eventId);

        if (teamRepository.hasPublishedScheduleOrPlay(eventId)) {
            throw conflict(
                    "Teams cannot be added after a schedule is published or play has started."
            );
        }

        Team team = new Team();
        team.setEventId(eventId);
        team.setActive(true);
        apply(team, request);

        teamRepository.saveAndFlush(team);
        entityManager.refresh(team);

        return TeamResponse.from(team);
    }

    @Transactional
    public TeamResponse update(
            Jwt jwt,
            UUID eventId,
            UUID teamId,
            TeamSaveRequest request
    ) {
        lockForManagement(jwt, eventId);

        Team team = teamRepository.findByIdAndEventId(teamId, eventId)
                .orElseThrow(() -> new EntityNotFoundException("Team not found."));

        if (!team.isActive()) {
            throw conflict("Inactive teams cannot be edited here.");
        }

        if (teamRepository.hasPlayHistory(eventId, teamId)) {
            throw conflict(
                    "This team is locked because its fixtures or rounds have progressed."
            );
        }

        apply(team, request);
        teamRepository.saveAndFlush(team);
        entityManager.refresh(team);

        return TeamResponse.from(team);
    }

    private void lockForManagement(Jwt jwt, UUID eventId) {
        UUID userId = userService.extractUserId(jwt);

        // Check before locking, then recheck after acquiring the lock.
        membershipService.requireOrganizer(eventId, userId);

        Event event = eventRepository.lockById(eventId)
                .orElseThrow(() -> new EntityNotFoundException("Event not found."));

        membershipService.requireOrganizer(eventId, userId);

        if ("completed".equals(event.getStatus())
                || "archived".equals(event.getStatus())) {
            throw conflict("Teams cannot be changed in a closed event.");
        }
    }

    private void apply(Team team, TeamSaveRequest request) {
        String name = request.name().strip();

        if (name.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Team name is required."
            );
        }

        team.setName(name);
        team.setColor(request.color());
        team.setSortOrder(request.sortOrder());
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}