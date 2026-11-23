package com.murisegames.api.modules.Event.Scoring.TeamResults;

import com.murisegames.api.utils.CustomApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

import static com.murisegames.api.modules.Event.Scoring.TeamResults.TeamResultsTypes.*;

@Tag(name = "Team results", description = "A team's own match results on the Player screen")
@RestController
@RequestMapping("/api/events/{eventId}/team-results")
@RequiredArgsConstructor
public class TeamResultsController {

    private final TeamResultsService teamResultsService;

    @Operation(summary = "Player: my team's results (detail depends on the host's mode)")
    @GetMapping
    public ResponseEntity<CustomApiResponse<MyResultsResponse>> mine(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID eventId
    ) {
        return ResponseEntity.ok(CustomApiResponse.success(
                teamResultsService.myResults(jwt, eventId),
                "Team results retrieved.",
                HttpStatus.OK.value()
        ));
    }

    @Operation(summary = "Organizer: what team phones show")
    @GetMapping("/settings")
    public ResponseEntity<CustomApiResponse<SettingResponse>> setting(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID eventId
    ) {
        return ResponseEntity.ok(CustomApiResponse.success(
                teamResultsService.setting(jwt, eventId),
                "Team results setting retrieved.",
                HttpStatus.OK.value()
        ));
    }

    @Operation(summary = "Organizer: change what team phones show")
    @PutMapping("/settings")
    public ResponseEntity<CustomApiResponse<SettingResponse>> updateSetting(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID eventId,
            @Valid @RequestBody SettingRequest request
    ) {
        return ResponseEntity.ok(CustomApiResponse.success(
                teamResultsService.updateSetting(jwt, eventId, request),
                "Team results setting saved.",
                HttpStatus.OK.value()
        ));
    }
}
