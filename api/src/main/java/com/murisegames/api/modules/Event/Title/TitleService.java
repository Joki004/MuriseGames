package com.murisegames.api.modules.Event.Title;

import com.murisegames.api.modules.Audit.AuditService;
import com.murisegames.api.modules.Event.Clue.SceneEntry;
import com.murisegames.api.modules.Event.Clue.SceneEntryRepository;
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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TitleService {

    private final SceneEntryRepository entryRepository;
    private final SceneSecretRepository secretRepository;
    private final TitleAliasRepository aliasRepository;
    private final TeamRepository teamRepository;
    private final EventRepository eventRepository;
    private final EventMembershipService membershipService;
    private final UserService userService;
    private final AuditService auditService;

    /** Titles are secret: only organizers may read them. */
    @Transactional(readOnly = true)
    public TitleBoardResponse board(Jwt jwt, UUID eventId) {
        UUID userId = userService.extractUserId(jwt);
        membershipService.requireOrganizer(eventId, userId);
        return buildBoard(eventId);
    }

    @Transactional
    public TitleBoardResponse save(
            Jwt jwt,
            UUID eventId,
            UUID teamId,
            TitleSaveRequest request
    ) {
        UUID userId = lockForManagement(jwt, eventId);

        teamRepository.findByIdAndEventId(teamId, eventId)
                .filter(Team::isActive)
                .orElseThrow(() -> new EntityNotFoundException("Team not found."));

        String title = request.title().strip();
        String normalizedTitle = TitleNormalizer.normalize(title);
        if (normalizedTitle.isEmpty()) {
            throw bad("The title must contain letters or digits.");
        }

        // Blank lines and duplicates are dropped; an alias equal to the title adds nothing.
        Map<String, String> aliases = new LinkedHashMap<>();
        for (String raw : request.aliases()) {
            String alias = raw == null ? "" : raw.strip();
            if (alias.isEmpty()) {
                continue;
            }
            String normalized = TitleNormalizer.normalize(alias);
            if (normalized.isEmpty()) {
                throw bad("Alias \"" + alias + "\" must contain letters or digits.");
            }
            if (!normalized.equals(normalizedTitle)) {
                aliases.putIfAbsent(normalized, alias);
            }
        }

        SceneEntry entry = entryRepository
                .findByEventIdAndTeamId(eventId, teamId)
                .orElseGet(() -> {
                    SceneEntry created = new SceneEntry();
                    created.setEventId(eventId);
                    created.setTeamId(teamId);
                    return entryRepository.saveAndFlush(created);
                });
        UUID sceneId = entry.getId();

        // GM-018: a revealed title is final; changing it would rewrite what was scored.
        if (entryRepository.isRevealed(eventId, sceneId)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This title has already been revealed and can no longer be changed."
            );
        }

        SceneSecret secret = secretRepository.findById(sceneId)
                .orElseGet(() -> {
                    SceneSecret created = new SceneSecret();
                    created.setSceneId(sceneId);
                    created.setEventId(eventId);
                    return created;
                });
        secret.setTitle(title);
        secret.setNormalizedTitle(normalizedTitle);
        secret.setAnswerScope(request.answerScope());
        secretRepository.saveAndFlush(secret);

        // Replace the alias list: delete first, flush, then insert.
        aliasRepository.deleteBySceneId(sceneId);
        aliasRepository.flush();

        List<TitleAlias> rows = new ArrayList<>();
        aliases.forEach((normalized, alias) -> {
            TitleAlias row = new TitleAlias();
            row.setEventId(eventId);
            row.setSceneId(sceneId);
            row.setAlias(alias);
            row.setNormalizedAlias(normalized);
            rows.add(row);
        });
        aliasRepository.saveAllAndFlush(rows);

        // The title itself is deliberately not written to the audit log.
        auditService.record(
                eventId, userId, "title.saved", "scene_secret", sceneId,
                Map.of("teamId", teamId.toString(), "aliasCount", aliases.size())
        );

        return buildBoard(eventId);
    }

    private TitleBoardResponse buildBoard(UUID eventId) {
        List<Team> teams = teamRepository
                .findByEventIdAndActiveTrueOrderBySortOrderAscNameAsc(eventId);

        Map<UUID, UUID> sceneByTeam = entryRepository.findByEventId(eventId)
                .stream()
                .collect(Collectors.toMap(SceneEntry::getTeamId, SceneEntry::getId));

        Map<UUID, SceneSecret> secretByScene = secretRepository
                .findByEventId(eventId)
                .stream()
                .collect(Collectors.toMap(SceneSecret::getSceneId, s -> s));

        Map<UUID, List<String>> aliasesByScene = aliasRepository
                .findByEventId(eventId)
                .stream()
                .sorted(Comparator.comparing(
                        (TitleAlias a) -> a.getAlias().toLowerCase(Locale.ROOT)))
                .collect(Collectors.groupingBy(
                        TitleAlias::getSceneId,
                        Collectors.mapping(TitleAlias::getAlias, Collectors.toList())));

        List<TitleBoardResponse.TeamTitle> rows = teams.stream()
                .map(team -> {
                    UUID sceneId = sceneByTeam.get(team.getId());
                    SceneSecret secret = secretByScene.get(sceneId);
                    return new TitleBoardResponse.TeamTitle(
                            team.getId(),
                            team.getName(),
                            team.getColor(),
                            secret == null ? null : secret.getTitle(),
                            secret == null ? null : secret.getAnswerScope(),
                            aliasesByScene.getOrDefault(sceneId, List.of())
                    );
                })
                .toList();

        return new TitleBoardResponse(rows);
    }

    private UUID lockForManagement(Jwt jwt, UUID eventId) {
        UUID userId = userService.extractUserId(jwt);
        membershipService.requireOrganizer(eventId, userId);

        Event event = eventRepository.lockById(eventId)
                .orElseThrow(() -> new EntityNotFoundException("Event not found."));

        membershipService.requireOrganizer(eventId, userId);

        if ("completed".equals(event.getStatus())
                || "archived".equals(event.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Titles cannot be changed in a closed event."
            );
        }
        return userId;
    }

    private ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}