package com.murisegames.api.modules.Event.EventGame;

import com.murisegames.api.modules.Event.EventGame.dto.EventGameAddRequest;
import com.murisegames.api.modules.Event.EventGame.dto.EventGameUpdateRequest;
import com.murisegames.api.utils.CustomApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Tag(name = "Event Games", description = "Event game lineup management")
@RestController
@RequestMapping("/api/events/{eventId}/games")
@RequiredArgsConstructor
public class EventGameController {

    private final EventGameService eventGameService;

    @Operation(summary = "List games in an event lineup")
    @GetMapping
    public ResponseEntity<CustomApiResponse<List<EventGameResponse>>> list(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID eventId
    ) {
        return ResponseEntity.ok(
                CustomApiResponse.success(
                        eventGameService.listLineup(jwt, eventId),
                        "Event lineup retrieved.",
                        HttpStatus.OK.value()
                )
        );
    }

    @Operation(summary = "Add a catalog game to the event lineup")
    @PostMapping
    public ResponseEntity<CustomApiResponse<EventGameResponse>> add(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID eventId,
            @Valid @RequestBody EventGameAddRequest request
    ) {
        EventGameResponse created = eventGameService.addGame(jwt, eventId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(CustomApiResponse.success(
                        created,
                        "Game added to event lineup.",
                        HttpStatus.CREATED.value()
                ));
    }

    @Operation(summary = "Update an event game's status, order, or notes")
    @PatchMapping("/{eventGameId}")
    public ResponseEntity<CustomApiResponse<EventGameResponse>> update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID eventId,
            @PathVariable UUID eventGameId,
            @Valid @RequestBody EventGameUpdateRequest request
    ) {
        EventGameResponse updated =
                eventGameService.updateGame(jwt, eventId, eventGameId, request);
        return ResponseEntity.ok(
                CustomApiResponse.success(
                        updated,
                        "Event game updated.",
                        HttpStatus.OK.value()
                )
        );
    }
}