package com.murisegames.api.modules.Event.Scoring;

import com.murisegames.api.modules.Event.Scoring.dto.ScoreEntryCreateRequest;
import com.murisegames.api.modules.Event.Scoring.dto.ScoreEntryReverseRequest;
import com.murisegames.api.modules.Event.Scoring.dto.ScoreboardVisibilityRequest;
import com.murisegames.api.utils.CustomApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Tag(name = "Scoring & Scoreboard", description = "Append-only score ledger and live/frozen standings")
@RestController
@RequestMapping("/api/events/{eventId}")
@RequiredArgsConstructor
public class ScoringController {

    private final ScoringService scoringService;

    @Operation(summary = "Get event scoreboard (live, frozen snapshot, or hidden)")
    @GetMapping("/scoreboard")
    public ResponseEntity<CustomApiResponse<ScoreboardResponse>> getScoreboard(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID eventId
    ) {
        return ResponseEntity.ok(
                CustomApiResponse.success(
                        scoringService.getScoreboard(jwt, eventId),
                        "Scoreboard retrieved.",
                        HttpStatus.OK.value()
                )
        );
    }

    @Operation(summary = "Update scoreboard visibility (public, hidden, or freeze snapshot)")
    @PatchMapping("/scoreboard/visibility")
    public ResponseEntity<CustomApiResponse<ScoreboardResponse>> updateVisibility(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID eventId,
            @Valid @RequestBody ScoreboardVisibilityRequest request
    ) {
        return ResponseEntity.ok(
                CustomApiResponse.success(
                        scoringService.updateVisibility(jwt, eventId, request.visibility()),
                        "Scoreboard visibility updated.",
                        HttpStatus.OK.value()
                )
        );
    }

    @Operation(summary = "List append-only score ledger entries (organizer only)")
    @GetMapping("/scores")
    public ResponseEntity<CustomApiResponse<List<ScoreEntryResponse>>> listEntries(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID eventId
    ) {
        return ResponseEntity.ok(
                CustomApiResponse.success(
                        scoringService.listEntries(jwt, eventId),
                        "Score entries retrieved.",
                        HttpStatus.OK.value()
                )
        );
    }

    @Operation(summary = "Append a score entry for a team")
    @PostMapping("/scores")
    public ResponseEntity<CustomApiResponse<ScoreEntryResponse>> addEntry(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID eventId,
            @Valid @RequestBody ScoreEntryCreateRequest request
    ) {
        ScoreEntryResponse created = scoringService.addEntry(jwt, eventId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(CustomApiResponse.success(
                        created,
                        "Score entry recorded.",
                        HttpStatus.CREATED.value()
                ));
    }

    @Operation(summary = "Reverse an existing score entry")
    @PostMapping("/scores/{entryId}/reverse")
    public ResponseEntity<CustomApiResponse<ScoreEntryResponse>> reverseEntry(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID eventId,
            @PathVariable UUID entryId,
            @Valid @RequestBody ScoreEntryReverseRequest request
    ) {
        ScoreEntryResponse reversal =
                scoringService.reverseEntry(jwt, eventId, entryId, request.reason());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(CustomApiResponse.success(
                        reversal,
                        "Score entry reversed.",
                        HttpStatus.CREATED.value()
                ));
    }
}