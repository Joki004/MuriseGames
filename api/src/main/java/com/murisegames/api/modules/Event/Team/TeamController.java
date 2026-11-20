package com.murisegames.api.modules.Event.Team;

import com.murisegames.api.modules.Event.Team.dto.TeamSaveRequest;
import com.murisegames.api.utils.CustomApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Teams", description = "Event teams and organizer management")
@RestController
@RequestMapping("/api/events/{eventId}/teams")
@RequiredArgsConstructor
public class TeamController {

    private final TeamService teamService;

    @GetMapping
    public ResponseEntity<CustomApiResponse<TeamListResponse>> list(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID eventId
    ) {
        return ResponseEntity.ok(CustomApiResponse.success(
                teamService.list(jwt, eventId),
                "Teams retrieved.",
                HttpStatus.OK.value()
        ));
    }

    @PostMapping
    public ResponseEntity<CustomApiResponse<TeamResponse>> create(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID eventId,
            @Valid @RequestBody TeamSaveRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(CustomApiResponse.success(
                        teamService.create(jwt, eventId, request),
                        "Team created.",
                        HttpStatus.CREATED.value()
                ));
    }

    @PatchMapping("/{teamId}")
    public ResponseEntity<CustomApiResponse<TeamResponse>> update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID eventId,
            @PathVariable UUID teamId,
            @Valid @RequestBody TeamSaveRequest request
    ) {
        return ResponseEntity.ok(CustomApiResponse.success(
                teamService.update(jwt, eventId, teamId, request),
                "Team updated.",
                HttpStatus.OK.value()
        ));
    }
}