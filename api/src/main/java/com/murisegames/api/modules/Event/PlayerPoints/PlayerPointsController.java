package com.murisegames.api.modules.Event.PlayerPoints;

import com.murisegames.api.modules.Event.PlayerPoints.PlayerPointsTypes.FixtureCreditsResponse;
import com.murisegames.api.modules.Event.PlayerPoints.PlayerPointsTypes.RankingResponse;
import com.murisegames.api.modules.Event.PlayerPoints.PlayerPointsTypes.SaveCreditsRequest;
import com.murisegames.api.utils.CustomApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Player Points", description = "Star players: personal points separate from team scores")
@RestController
@RequestMapping("/api/events/{eventId}")
@RequiredArgsConstructor
public class PlayerPointsController {

    private final PlayerPointsService service;

    @Operation(summary = "Players of a match and who is credited")
    @GetMapping("/fixtures/{fixtureId}/player-points")
    public ResponseEntity<CustomApiResponse<FixtureCreditsResponse>> fixtureCredits(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID eventId,
            @PathVariable UUID fixtureId
    ) {
        return ok(service.fixtureCredits(jwt, eventId, fixtureId), "Match players retrieved.");
    }

    @Operation(summary = "Set who is credited in a match (the full list; empty clears)")
    @PutMapping("/fixtures/{fixtureId}/player-points")
    public ResponseEntity<CustomApiResponse<FixtureCreditsResponse>> saveFixtureCredits(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID eventId,
            @PathVariable UUID fixtureId,
            @RequestBody SaveCreditsRequest request
    ) {
        return ok(
                service.saveFixtureCredits(jwt, eventId, fixtureId, request.credits()),
                "Star players saved."
        );
    }

    @Operation(summary = "Star players ranking")
    @GetMapping("/player-points/ranking")
    public ResponseEntity<CustomApiResponse<RankingResponse>> ranking(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID eventId
    ) {
        return ok(service.ranking(jwt, eventId), "Star players retrieved.");
    }

    private static <T> ResponseEntity<CustomApiResponse<T>> ok(T data, String message) {
        return ResponseEntity.ok(CustomApiResponse.success(data, message, HttpStatus.OK.value()));
    }
}
