package com.murisegames.api.modules.Event.Scoring.Adapter;

import com.murisegames.api.utils.CustomApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/events/{eventId}/scoring-preview")
public class ScorePreviewController {

    private final ScorePreviewService service;

    @GetMapping("/games")
    public CustomApiResponse<List<ScorePreviewService.GameOption>> games(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID eventId
    ) {
        return CustomApiResponse.success(
                service.options(jwt, eventId),
                "Scoring options retrieved.",
                200
        );
    }

    @PostMapping
    public CustomApiResponse<ScorePreviewService.PreviewResponse> preview(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID eventId,
            @RequestBody ScorePreviewService.PreviewRequest request
    ) {
        return CustomApiResponse.success(
                service.preview(jwt, eventId, request),
                "Score preview calculated.",
                200
        );
    }
}