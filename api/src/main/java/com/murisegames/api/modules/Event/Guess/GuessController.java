package com.murisegames.api.modules.Event.Guess;

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

import static com.murisegames.api.modules.Event.Guess.GuessTypes.*;

@Tag(name = "Guesses", description = "Team captain secret-title guesses with neutral receipts")
@RestController
@RequestMapping("/api/events/{eventId}/guesses")
@RequiredArgsConstructor
public class GuessController {

    private final GuessService guessService;

    @GetMapping
    public ResponseEntity<CustomApiResponse<CaptainGuessBoardResponse>> getBoard(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID eventId
    ) {
        return ResponseEntity.ok(CustomApiResponse.success(
                guessService.getCaptainBoard(jwt, eventId),
                "Guessing board retrieved.",
                HttpStatus.OK.value()
        ));
    }

    @PostMapping
    public ResponseEntity<CustomApiResponse<GuessReceipt>> submit(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID eventId,
            @Valid @RequestBody SubmitGuessRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(CustomApiResponse.success(
                guessService.submitGuess(jwt, eventId, request),
                "Guess received.",
                HttpStatus.CREATED.value()
        ));
    }
}