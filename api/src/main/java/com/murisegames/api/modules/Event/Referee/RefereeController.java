package com.murisegames.api.modules.Event.Referee;

import com.murisegames.api.utils.CustomApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

import static com.murisegames.api.modules.Event.Referee.RefereeTypes.*;

@Tag(name = "Referees", description = "Per-game referee assignments and the referee desk")
@RestController
@RequestMapping("/api/events/{eventId}/referees")
@RequiredArgsConstructor
public class RefereeController {

    private final RefereeService refereeService;

    @Operation(summary = "Organizer: referees, scorable games and current assignments")
    @GetMapping
    public ResponseEntity<CustomApiResponse<RefereeBoardResponse>> board(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID eventId
    ) {
        return ResponseEntity.ok(CustomApiResponse.success(
                refereeService.board(jwt, eventId),
                "Referee assignments retrieved.",
                HttpStatus.OK.value()
        ));
    }

    @Operation(summary = "Organizer: assign a referee to a game")
    @PostMapping("/assignments")
    public ResponseEntity<CustomApiResponse<RefereeBoardResponse>> assign(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID eventId,
            @Valid @RequestBody AssignRequest request
    ) {
        return ResponseEntity.ok(CustomApiResponse.success(
                refereeService.assign(jwt, eventId, request),
                "Referee assigned.",
                HttpStatus.OK.value()
        ));
    }

    @Operation(summary = "Organizer: remove a referee assignment")
    @DeleteMapping("/assignments/{assignmentId}")
    public ResponseEntity<CustomApiResponse<RefereeBoardResponse>> unassign(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID eventId,
            @PathVariable UUID assignmentId
    ) {
        return ResponseEntity.ok(CustomApiResponse.success(
                refereeService.unassign(jwt, eventId, assignmentId),
                "Referee assignment removed.",
                HttpStatus.OK.value()
        ));
    }

    @Operation(summary = "Referee: my assigned games and their matches")
    @GetMapping("/me")
    public ResponseEntity<CustomApiResponse<RefereeDeskResponse>> desk(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID eventId
    ) {
        return ResponseEntity.ok(CustomApiResponse.success(
                refereeService.desk(jwt, eventId),
                "Referee desk retrieved.",
                HttpStatus.OK.value()
        ));
    }
}
