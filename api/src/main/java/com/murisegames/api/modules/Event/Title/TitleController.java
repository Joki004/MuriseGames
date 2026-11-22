package com.murisegames.api.modules.Event.Title;

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

@Tag(name = "Titles", description = "Secret Scene titles and aliases (organizer only)")
@RestController
@RequestMapping("/api/events/{eventId}")
@RequiredArgsConstructor
public class TitleController {

    private final TitleService titleService;

    @GetMapping("/titles")
    public ResponseEntity<CustomApiResponse<TitleBoardResponse>> board(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID eventId
    ) {
        return ResponseEntity.ok(CustomApiResponse.success(
                titleService.board(jwt, eventId),
                "Titles retrieved.",
                HttpStatus.OK.value()
        ));
    }

    @PutMapping("/teams/{teamId}/title")
    public ResponseEntity<CustomApiResponse<TitleBoardResponse>> save(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID eventId,
            @PathVariable UUID teamId,
            @Valid @RequestBody TitleSaveRequest request
    ) {
        return ResponseEntity.ok(CustomApiResponse.success(
                titleService.save(jwt, eventId, teamId, request),
                "Title saved.",
                HttpStatus.OK.value()
        ));
    }
}