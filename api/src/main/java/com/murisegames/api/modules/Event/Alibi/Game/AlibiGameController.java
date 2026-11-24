package com.murisegames.api.modules.Event.Alibi.Game;

import com.murisegames.api.modules.Event.Alibi.Game.AlibiGameTypes.*;
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

@Tag(name = "Alibi game", description = "Playing Unified Alibi: host, judge, player and TV views")
@RestController
@RequestMapping("/api/events/{eventId}/alibi")
@RequiredArgsConstructor
public class AlibiGameController {

    private final AlibiGameService service;

    // ---- host -------------------------------------------------------------------------

    @GetMapping("/host")
    public ResponseEntity<CustomApiResponse<HostView>> host(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID eventId) {
        return ok(service.host(jwt, eventId), "Alibi host view retrieved.");
    }

    @PostMapping("/sessions")
    public ResponseEntity<CustomApiResponse<HostView>> createSession(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID eventId,
            @Valid @RequestBody CreateSessionRequest request) {
        return ok(service.createSession(jwt, eventId, request), "Alibi session ready.");
    }

    @PutMapping("/rounds/{roundId}/setup")
    public ResponseEntity<CustomApiResponse<HostView>> setup(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID eventId, @PathVariable UUID roundId,
            @Valid @RequestBody SetupRequest request) {
        return ok(service.setup(jwt, eventId, roundId, request), "Round set up.");
    }

    @PostMapping("/rounds/{roundId}/start")
    public ResponseEntity<CustomApiResponse<HostView>> start(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID eventId, @PathVariable UUID roundId) {
        return ok(service.start(jwt, eventId, roundId), "Preparation started.");
    }

    @PostMapping("/rounds/{roundId}/questions")
    public ResponseEntity<CustomApiResponse<HostView>> startQuestions(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID eventId, @PathVariable UUID roundId) {
        return ok(service.startQuestions(jwt, eventId, roundId), "Questions started.");
    }

    @PostMapping("/rounds/{roundId}/skip")
    public ResponseEntity<CustomApiResponse<HostView>> skip(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID eventId, @PathVariable UUID roundId) {
        return ok(service.skipQuestion(jwt, eventId, roundId), "Question closed.");
    }

    @PostMapping("/rounds/{roundId}/reveal")
    public ResponseEntity<CustomApiResponse<HostView>> reveal(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID eventId, @PathVariable UUID roundId) {
        return ok(service.revealNext(jwt, eventId, roundId), "Question revealed.");
    }

    @PostMapping("/sessions/{sessionId}/record")
    public ResponseEntity<CustomApiResponse<HostView>> record(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID eventId, @PathVariable UUID sessionId) {
        return ok(service.record(jwt, eventId, sessionId), "Result recorded.");
    }

    // ---- judge ------------------------------------------------------------------------

    @GetMapping("/judge")
    public ResponseEntity<CustomApiResponse<JudgeView>> judge(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID eventId) {
        return ok(service.judge(jwt, eventId), "Alibi judge view retrieved.");
    }

    @PutMapping("/prompts/{promptId}/verdict")
    public ResponseEntity<CustomApiResponse<JudgeView>> verdict(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID eventId, @PathVariable UUID promptId,
            @Valid @RequestBody VerdictRequest request) {
        return ok(service.verdict(jwt, eventId, promptId, request), "Verdict saved.");
    }

    // ---- player -----------------------------------------------------------------------

    @GetMapping("/me")
    public ResponseEntity<CustomApiResponse<PlayerView>> me(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID eventId) {
        return ok(service.me(jwt, eventId), "Alibi player view retrieved.");
    }

    @PostMapping("/prompts/{promptId}/answer")
    public ResponseEntity<CustomApiResponse<PlayerView>> answer(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID eventId, @PathVariable UUID promptId,
            @Valid @RequestBody AnswerRequest request) {
        return ok(service.answer(jwt, eventId, promptId, request), "Answer locked.");
    }

    // ---- TV ---------------------------------------------------------------------------

    @GetMapping("/live")
    public ResponseEntity<CustomApiResponse<PublicView>> live(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID eventId) {
        return ok(service.live(jwt, eventId), "Alibi live view retrieved.");
    }

    private static <T> ResponseEntity<CustomApiResponse<T>> ok(T data, String message) {
        return ResponseEntity.ok(CustomApiResponse.success(data, message, HttpStatus.OK.value()));
    }
}
