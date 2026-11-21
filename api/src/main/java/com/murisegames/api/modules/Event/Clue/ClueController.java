package com.murisegames.api.modules.Event.Clue;

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

@Tag(name = "Clues", description = "Clue authoring (organizer only)")
@RestController
@RequestMapping("/api/events/{eventId}")
@RequiredArgsConstructor
public class ClueController {

    private final ClueService clueService;

    @GetMapping("/clues")
    public ResponseEntity<CustomApiResponse<ClueBoardResponse>> board(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID eventId
    ) {
        return ResponseEntity.ok(CustomApiResponse.success(
                clueService.board(jwt, eventId),
                "Clues retrieved.",
                HttpStatus.OK.value()
        ));
    }

    @PutMapping("/teams/{teamId}/clues")
    public ResponseEntity<CustomApiResponse<ClueBoardResponse>> save(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID eventId,
            @PathVariable UUID teamId,
            @Valid @RequestBody ClueSetSaveRequest request
    ) {
        return ResponseEntity.ok(CustomApiResponse.success(
                clueService.save(jwt, eventId, teamId, request),
                "Clues saved.",
                HttpStatus.OK.value()
        ));
    }
}