package com.murisegames.api.modules.Event.EventGame;

import com.murisegames.api.modules.Catalog.GameDefinition;
import com.murisegames.api.modules.Catalog.GameDefinitionRepository;
import com.murisegames.api.modules.Catalog.GameVersion;
import com.murisegames.api.modules.Catalog.GameVersionRepository;
import com.murisegames.api.modules.Event.EventGame.dto.EventGameAddRequest;
import com.murisegames.api.modules.Event.EventGame.dto.EventGameUpdateRequest;
import com.murisegames.api.modules.Event.Membership.EventMembershipService;
import com.murisegames.api.modules.User.UserService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EventGameService {

    private final EventGameRepository eventGameRepository;
    private final GameDefinitionRepository definitionRepository;
    private final GameVersionRepository versionRepository;
    private final EventMembershipService membershipService;
    private final UserService userService;
    private final EntityManager entityManager;

    @Transactional(readOnly = true)
    public List<EventGameResponse> listLineup(Jwt jwt, UUID eventId) {
        UUID actorId = userService.extractUserId(jwt);
        membershipService.requireAccess(eventId, actorId);

        List<EventGame> games =
                eventGameRepository.findByEventIdOrderBySortOrderAscCreatedAtAsc(eventId);

        Set<UUID> defIds = games.stream()
                .map(EventGame::getGameDefinitionId)
                .collect(Collectors.toSet());
        Set<UUID> verIds = games.stream()
                .map(EventGame::getGameVersionId)
                .collect(Collectors.toSet());

        Map<UUID, GameDefinition> defs = definitionRepository.findAllById(defIds)
                .stream()
                .collect(Collectors.toMap(GameDefinition::getId, Function.identity()));
        Map<UUID, GameVersion> vers = versionRepository.findAllById(verIds)
                .stream()
                .collect(Collectors.toMap(GameVersion::getId, Function.identity()));

        return games.stream()
                .map(eg -> EventGameResponse.from(
                        eg,
                        defs.get(eg.getGameDefinitionId()),
                        vers.get(eg.getGameVersionId())
                ))
                .toList();
    }

    @Transactional
    public EventGameResponse addGame(Jwt jwt, UUID eventId, EventGameAddRequest request) {
        UUID actorId = userService.extractUserId(jwt);
        membershipService.requireOrganizer(eventId, actorId);

        if (eventGameRepository.existsByEventIdAndGameDefinitionId(
                eventId, request.gameDefinitionId())) {
            throw new IllegalStateException("This game is already in the event lineup.");
        }

        GameDefinition definition = definitionRepository.findById(request.gameDefinitionId())
                .orElseThrow(() -> new EntityNotFoundException("Game definition not found."));

        GameVersion version = (request.gameVersionId() != null)
                ? versionRepository
                .findByIdAndGameDefinitionId(request.gameVersionId(), definition.getId())
                .orElseThrow(() -> new EntityNotFoundException("Game version not found."))
                : versionRepository
                .findTopByGameDefinitionIdAndStatusOrderByVersionDesc(
                        definition.getId(), "published")
                .orElseThrow(() -> new IllegalStateException(
                        "No published version exists for this game."));

        EventGame eventGame = new EventGame();
        eventGame.setEventId(eventId);
        eventGame.setGameDefinitionId(definition.getId());
        eventGame.setGameVersionId(version.getId());
        eventGame.setStatus("scheduled");
        eventGame.setSortOrder(request.sortOrder() != null ? request.sortOrder() : 0);
        eventGame.setHouseRulesNote(request.houseRulesNote());
        eventGame.setScoreboardVisibilityOverride(request.scoreboardVisibilityOverride());
        eventGame.setSettingsSnapshot(request.settingsSnapshot() != null
                ? new HashMap<>(request.settingsSnapshot())
                : new HashMap<>());

        eventGameRepository.saveAndFlush(eventGame);
        entityManager.refresh(eventGame);

        return EventGameResponse.from(eventGame, definition, version);
    }

    @Transactional
    public EventGameResponse updateGame(
            Jwt jwt,
            UUID eventId,
            UUID eventGameId,
            EventGameUpdateRequest request
    ) {
        UUID actorId = userService.extractUserId(jwt);
        membershipService.requireOrganizer(eventId, actorId);

        EventGame eventGame = eventGameRepository.findByIdAndEventId(eventGameId, eventId)
                .orElseThrow(() -> new EntityNotFoundException("Event game not found."));

        if (request.status() != null) {
            eventGame.setStatus(request.status());
        }
        if (request.sortOrder() != null) {
            eventGame.setSortOrder(request.sortOrder());
        }
        if (request.houseRulesNote() != null) {
            eventGame.setHouseRulesNote(request.houseRulesNote());
        }
        if (request.scoreboardVisibilityOverride() != null) {
            eventGame.setScoreboardVisibilityOverride(request.scoreboardVisibilityOverride());
        }

        eventGameRepository.saveAndFlush(eventGame);
        entityManager.refresh(eventGame);

        GameDefinition def = definitionRepository.findById(eventGame.getGameDefinitionId()).orElse(null);
        GameVersion ver = versionRepository.findById(eventGame.getGameVersionId()).orElse(null);

        return EventGameResponse.from(eventGame, def, ver);
    }
}