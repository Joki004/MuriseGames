package com.murisegames.api.modules.Event.Finale;

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

import static com.murisegames.api.modules.Event.Finale.FinaleTypes.*;

@Tag(name = "Finale", description = "Secret Scene guess reviews, title reveals and finale scoring (GM-018)")
@RestController
@RequestMapping("/api/events/{eventId}/finale")
@RequiredArgsConstructor
public class FinaleController {

    private final FinaleService finaleService;

    /** Organizer: every guess with its automatic match and host decision. */
    @GetMapping("/reviews")
    public ResponseEntity<CustomApiResponse<ReviewBoardResponse>> reviews(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID eventId
    ) {
        return ResponseEntity.ok(CustomApiResponse.success(
                finaleService.reviewBoard(jwt, eventId),
                "Guess reviews retrieved.",
                HttpStatus.OK.value()
        ));
    }

    /** Organizer: mark one guess correct or wrong (allowed until that title is revealed). */
    @PutMapping("/reviews/{guessId}")
    public ResponseEntity<CustomApiResponse<ReviewBoardResponse>> decide(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID eventId,
            @PathVariable UUID guessId,
            @Valid @RequestBody ReviewDecisionRequest request
    ) {
        return ResponseEntity.ok(CustomApiResponse.success(
                finaleService.decide(jwt, eventId, guessId, request),
                "Guess review saved.",
                HttpStatus.OK.value()
        ));
    }

    /** Organizer: a title leaked before the lock; every other team gets +2 (added on top). */
    @PostMapping("/leaks")
    public ResponseEntity<CustomApiResponse<ReviewBoardResponse>> recordLeak(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID eventId,
            @Valid @RequestBody LeakRequest request
    ) {
        return ResponseEntity.ok(CustomApiResponse.success(
                finaleService.recordLeak(jwt, eventId, request),
                "Leak recorded.",
                HttpStatus.OK.value()
        ));
    }

    /** Anyone in the event (players, TV): revealed titles and their results only. */
    @GetMapping("/reveals")
    public ResponseEntity<CustomApiResponse<RevealListResponse>> reveals(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID eventId
    ) {
        return ResponseEntity.ok(CustomApiResponse.success(
                finaleService.listReveals(jwt, eventId),
                "Reveals retrieved.",
                HttpStatus.OK.value()
        ));
    }

    /** Organizer: reveal one team's title and settle its points once. */
    @PostMapping("/reveals")
    public ResponseEntity<CustomApiResponse<RevealView>> reveal(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID eventId,
            @Valid @RequestBody RevealRequest request
    ) {
        return ResponseEntity.ok(CustomApiResponse.success(
                finaleService.reveal(jwt, eventId, request),
                "Title revealed.",
                HttpStatus.OK.value()
        ));
    }
}
