package com.murisegames.api.modules.Event.Live;

import com.murisegames.api.utils.CustomApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/events/{eventId}")
@RequiredArgsConstructor
public class EventLiveController {

    private final EventLiveService liveService;

    @GetMapping("/snapshot")
    public ResponseEntity<CustomApiResponse<EventSnapshotResponse>> snapshot(
            @PathVariable UUID eventId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        UUID actorId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore().cachePrivate())
                .body(CustomApiResponse.success(
                        liveService.getSnapshot(eventId, actorId),
                        "Event snapshot retrieved.",
                        HttpStatus.OK.value()
                ));
    }

    @PutMapping("/state")
    public ResponseEntity<CustomApiResponse<EventSnapshotResponse>> updateState(
            @PathVariable UUID eventId,
            @Valid @RequestBody EventStateUpdateRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        UUID actorId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore().cachePrivate())
                .body(CustomApiResponse.success(
                        liveService.updateState(eventId, request, actorId),
                        "Event state updated.",
                        HttpStatus.OK.value()
                ));
    }

    @PostMapping("/state/pause")
    public ResponseEntity<CustomApiResponse<EventSnapshotResponse>> pause(
            @PathVariable UUID eventId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        UUID actorId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore().cachePrivate())
                .body(CustomApiResponse.success(
                        liveService.pauseTimer(eventId, actorId),
                        "Event timer paused.",
                        HttpStatus.OK.value()
                ));
    }

    @PostMapping("/state/resume")
    public ResponseEntity<CustomApiResponse<EventSnapshotResponse>> resume(
            @PathVariable UUID eventId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        UUID actorId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore().cachePrivate())
                .body(CustomApiResponse.success(
                        liveService.resumeTimer(eventId, actorId),
                        "Event timer resumed.",
                        HttpStatus.OK.value()
                ));
    }
}