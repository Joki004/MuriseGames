package com.murisegames.api.modules.Event.Scoring.Settlement;

import com.murisegames.api.utils.CustomApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

import static com.murisegames.api.modules.Event.Scoring.Settlement.OfficialResultTypes.*;

@RestController
@RequestMapping("/api/v1/events/{eventId}")
@RequiredArgsConstructor
public class OfficialResultController {

    private final OfficialResultService resultService;

    @PutMapping("/fixtures/{fixtureId}/result")
    public ResponseEntity<CustomApiResponse<FixtureSettlementResponse>> recordResult(
            @PathVariable UUID eventId,
            @PathVariable UUID fixtureId,
            @Valid @RequestBody RecordFixtureResultRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ResponseEntity.ok(CustomApiResponse.success(
                resultService.recordFixtureResult(jwt, eventId, fixtureId, request),
                "Official fixture result recorded.",
                HttpStatus.OK.value()
        ));
    }

    @PostMapping("/fixtures/{fixtureId}/cancel")
    public ResponseEntity<CustomApiResponse<Void>> cancelFixture(
            @PathVariable UUID eventId,
            @PathVariable UUID fixtureId,
            @Valid @RequestBody CancelFixtureRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        resultService.cancelFixture(jwt, eventId, fixtureId, request);
        return ResponseEntity.ok(CustomApiResponse.success(
                null,
                "Fixture cancelled; scoring denominator remains frozen.",
                HttpStatus.OK.value()
        ));
    }

    @GetMapping("/scoring/ranking-status")
    public ResponseEntity<CustomApiResponse<RankingStatusResponse>> rankingStatus(
            @PathVariable UUID eventId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ResponseEntity.ok(CustomApiResponse.success(
                resultService.getRankingStatus(jwt, eventId),
                "Ranking status retrieved.",
                HttpStatus.OK.value()
        ));
    }
}