package com.murisegames.api.modules.Event.Roster;

import com.murisegames.api.modules.Event.Roster.EventRosterTypes.EventRosterResponse;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Event Roster", description = "Teams, members and their pictures for screens")
@RestController
@RequestMapping("/api/events/{eventId}/roster")
@RequiredArgsConstructor
public class EventRosterController {

    private final EventRosterService rosterService;

    @Operation(summary = "Teams with their picture and members with their avatar")
    @GetMapping
    public ResponseEntity<CustomApiResponse<EventRosterResponse>> roster(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID eventId
    ) {
        return ResponseEntity.ok(
                CustomApiResponse.success(
                        rosterService.roster(jwt, eventId),
                        "Roster retrieved.",
                        HttpStatus.OK.value()
                )
        );
    }
}
