package com.murisegames.api.modules.Event.Scoring.Plan;

import com.murisegames.api.utils.CustomApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

import static com.murisegames.api.modules.Event.Scoring.Plan.ScoringPlanTypes.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/events/{eventId}/scoring-plan")
public class ScoringPlanController {

    private final ScoringPlanService service;

    @GetMapping
    public CustomApiResponse<Setup> setup(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID eventId
    ) {
        return CustomApiResponse.success(
                service.setup(jwt, eventId), "Scoring plan retrieved.", 200
        );
    }

    @PostMapping("/review")
    public CustomApiResponse<Review> review(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID eventId,
            @Valid @RequestBody Selection selection
    ) {
        return CustomApiResponse.success(
                service.review(jwt, eventId, selection),
                "Scoring plan reviewed.",
                200
        );
    }

    @PostMapping("/publish")
    public CustomApiResponse<Published> publish(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID eventId,
            @Valid @RequestBody PublishRequest request
    ) {
        return CustomApiResponse.success(
                service.publish(jwt, eventId, request),
                "Scoring plan published.",
                200
        );
    }
}