package com.murisegames.api.modules.Event.Clue;

import com.murisegames.api.modules.Event.Event;
import com.murisegames.api.modules.Event.EventRepository;
import com.murisegames.api.modules.Event.Membership.EventMembershipService;
import com.murisegames.api.modules.Event.Team.Team;
import com.murisegames.api.modules.Event.Team.TeamRepository;
import com.murisegames.api.modules.User.UserService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ClueService {

    public static final int STAGES = 3;

    private final TeamRepository teamRepository;
    private final SceneEntryRepository sceneEntryRepository;
    private final SceneClueRepository sceneClueRepository;
    private final EventRepository eventRepository;
    private final EventMembershipService membershipService;
    private final UserService userService;

    @Transactional(readOnly = true)
    public ClueBoardResponse board(Jwt jwt, UUID eventId) {
        membershipService.requireOrganizer(eventId, userService.extractUserId(jwt));
        return buildBoard(eventId);
    }

    @Transactional
    public ClueBoardResponse save(
            Jwt jwt,
            UUID eventId,
            UUID teamId,
            ClueSetSaveRequest request
    ) {
        UUID userId = userService.extractUserId(jwt);

        // Check before locking, then recheck after acquiring the lock (same as TeamService).
        membershipService.requireOrganizer(eventId, userId);

        Event event = eventRepository.lockById(eventId)
                .orElseThrow(() -> new EntityNotFoundException("Event not found."));

        membershipService.requireOrganizer(eventId, userId);

        if ("completed".equals(event.getStatus()) || "archived".equals(event.getStatus())) {
            throw conflict("Clues cannot be changed in a closed event.");
        }

        teamRepository.findByIdAndEventId(teamId, eventId)
                .filter(Team::isActive)
                .orElseThrow(() -> new EntityNotFoundException("Team not found."));

        SceneEntry entry = sceneEntryRepository.findByEventIdAndTeamId(eventId, teamId)
                .orElseGet(() -> {
                    SceneEntry created = new SceneEntry();
                    created.setEventId(eventId);
                    created.setTeamId(teamId);
                    return sceneEntryRepository.saveAndFlush(created);
                });

        Map<Integer, SceneClue> existing = sceneClueRepository
                .findBySceneId(entry.getId())
                .stream()
                .collect(Collectors.toMap(SceneClue::getStage, clue -> clue));

        List<ClueSetSaveRequest.Item> items = request.clues();

        for (int i = 0; i < STAGES; i++) {
            int stage = i + 1;
            String body = items.get(i).body().strip();
            boolean approved = items.get(i).approved();
            SceneClue current = existing.get(stage);

            if (body.isEmpty()) {
                if (approved) {
                    throw bad("Clue " + stage + " is empty, so it cannot be approved.");
                }
                if (current != null) {
                    sceneClueRepository.delete(current);
                }
                continue;
            }

            SceneClue clue = current != null ? current : new SceneClue();
            if (current == null) {
                clue.setEventId(eventId);
                clue.setSceneId(entry.getId());
                clue.setStage(stage);
            }

            boolean bodyChanged = current == null || !body.equals(current.getBody());
            clue.setBody(body);

            if (approved) {
                if (bodyChanged || !clue.isApproved()) {
                    clue.setApprovedBy(userId);
                    clue.setApprovedAt(Instant.now());
                }
                clue.setApproved(true);
            } else {
                clue.setApproved(false);
                clue.setApprovedBy(null);
                clue.setApprovedAt(null);
            }

            sceneClueRepository.save(clue);
        }

        sceneClueRepository.flush();
        return buildBoard(eventId);
    }

    /**
     * Release gate. The release command calls this inside its own transaction
     * and aborts if it throws: a stage cannot be released until every active
     * team has an approved clue for it.
     */
    @Transactional(readOnly = true)
    public void requireStageReleasable(UUID eventId, int stage) {
        if (stage < 1 || stage > STAGES) {
            throw bad("Clue stage must be between 1 and " + STAGES + ".");
        }

        ClueBoardResponse.Stage status = buildBoard(eventId).stages().get(stage - 1);

        if (!status.canRelease()) {
            throw conflict(
                    "Clue " + stage + " cannot be released: "
                            + (status.totalTeams() - status.approvedTeams()) + " of "
                            + status.totalTeams() + " teams have no approved clue for it."
            );
        }
    }

    private ClueBoardResponse buildBoard(UUID eventId) {
        List<Team> teams = teamRepository
                .findByEventIdAndActiveTrueOrderBySortOrderAscNameAsc(eventId);

        Map<UUID, UUID> sceneByTeam = sceneEntryRepository.findByEventId(eventId)
                .stream()
                .collect(Collectors.toMap(SceneEntry::getTeamId, SceneEntry::getId));

        Map<UUID, Map<Integer, SceneClue>> cluesByScene = sceneClueRepository
                .findByEventId(eventId)
                .stream()
                .collect(Collectors.groupingBy(
                        SceneClue::getSceneId,
                        Collectors.toMap(SceneClue::getStage, clue -> clue)
                ));

        int[] approvedPerStage = new int[STAGES + 1];
        List<ClueBoardResponse.TeamClues> rows = new ArrayList<>();

        for (Team team : teams) {
            Map<Integer, SceneClue> byStage = cluesByScene.getOrDefault(
                    sceneByTeam.get(team.getId()),
                    Map.of()
            );

            List<ClueBoardResponse.Clue> clues = new ArrayList<>();
            for (int stage = 1; stage <= STAGES; stage++) {
                SceneClue clue = byStage.get(stage);
                boolean approved = clue != null && clue.isApproved();
                if (approved) {
                    approvedPerStage[stage]++;
                }
                clues.add(new ClueBoardResponse.Clue(
                        stage,
                        clue == null ? "" : clue.getBody(),
                        approved
                ));
            }

            rows.add(new ClueBoardResponse.TeamClues(
                    team.getId(), team.getName(), team.getColor(), clues
            ));
        }

        List<ClueBoardResponse.Stage> stages = new ArrayList<>();
        for (int stage = 1; stage <= STAGES; stage++) {
            stages.add(new ClueBoardResponse.Stage(
                    stage,
                    approvedPerStage[stage],
                    teams.size(),
                    !teams.isEmpty() && approvedPerStage[stage] == teams.size()
            ));
        }

        return new ClueBoardResponse(rows, stages);
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    private ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}