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

import static com.murisegames.api.modules.Event.Clue.ClueReleaseTypes.*;

@Tag(name = "Clues", description = "Atomic clue stage releases and public released clues")
@RestController
@RequestMapping("/api/events/{eventId}/clues")
@RequiredArgsConstructor
public class ClueReleaseController {

    private final ClueReleaseService releaseService;

    @GetMapping("/released")
    public ResponseEntity<CustomApiResponse<ReleasedCluesResponse>> getReleased(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID eventId
    ) {
        return ResponseEntity.ok(CustomApiResponse.success(
                releaseService.getReleasedClues(jwt, eventId),
                "Released clues retrieved.",
                HttpStatus.OK.value()
        ));
    }

    @PostMapping("/release")
    public ResponseEntity<CustomApiResponse<ReleasedCluesResponse>> releaseNext(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID eventId,
            @Valid @RequestBody StageCommandRequest request
    ) {
        return ResponseEntity.ok(CustomApiResponse.success(
                releaseService.releaseNextStage(jwt, eventId, request),
                "Clue stage released.",
                HttpStatus.OK.value()
        ));
    }

    @PostMapping("/close-guessing")
    public ResponseEntity<CustomApiResponse<ReleasedCluesResponse>> closeGuessing(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID eventId,
            @Valid @RequestBody StageCommandRequest request
    ) {
        return ResponseEntity.ok(CustomApiResponse.success(
                releaseService.closeGuessing(jwt, eventId, request),
                "All guessing closed.",
                HttpStatus.OK.value()
        ));
    }
}