package com.murisegames.api.modules.Picture;

import com.murisegames.api.utils.CustomApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/events/{eventId}/media")
@RequiredArgsConstructor
public class EventMediaController {

    private final EventMediaService eventMediaService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CustomApiResponse<EventMediaResponse>> upload(
            @PathVariable UUID eventId,
            @RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal Jwt jwt
    ) {
        UUID actorId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.status(HttpStatus.CREATED).body(
                CustomApiResponse.success(
                        eventMediaService.upload(eventId, actorId, file),
                        "Media uploaded.",
                        HttpStatus.CREATED.value()
                )
        );
    }

    @GetMapping("/{assetId}")
    public ResponseEntity<CustomApiResponse<EventMediaResponse>> read(
            @PathVariable UUID eventId,
            @PathVariable UUID assetId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        UUID actorId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(
                CustomApiResponse.success(
                        eventMediaService.read(eventId, assetId, actorId),
                        "Media retrieved.",
                        HttpStatus.OK.value()
                )
        );
    }

    @PostMapping("/{assetId}/release")
    public ResponseEntity<CustomApiResponse<EventMediaResponse>> release(
            @PathVariable UUID eventId,
            @PathVariable UUID assetId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        UUID actorId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(
                CustomApiResponse.success(
                        eventMediaService.release(eventId, assetId, actorId),
                        "Media released.",
                        HttpStatus.OK.value()
                )
        );
    }

    @DeleteMapping("/{assetId}")
    public ResponseEntity<CustomApiResponse<Void>> delete(
            @PathVariable UUID eventId,
            @PathVariable UUID assetId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        UUID actorId = UUID.fromString(jwt.getSubject());
        eventMediaService.delete(eventId, assetId, actorId);
        return ResponseEntity.ok(
                CustomApiResponse.success(null, "Media scheduled for deletion.", HttpStatus.OK.value())
        );
    }
}