package com.murisegames.api.modules.Event.Invite;

import com.murisegames.api.modules.Event.Invite.dto.InviteCreateRequest;
import com.murisegames.api.utils.CustomApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Invites", description = "Organizer invitation management")
@RestController
@RequestMapping("/api/events/{eventId}/invites")
@RequiredArgsConstructor
public class EventInviteController {

    private final EventInviteService inviteService;

    @GetMapping
    public ResponseEntity<CustomApiResponse<List<EventInviteResponse>>> list(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID eventId
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(CustomApiResponse.success(
                        inviteService.list(jwt, eventId),
                        "Invites retrieved.",
                        HttpStatus.OK.value()
                ));
    }

    @PostMapping
    public ResponseEntity<CustomApiResponse<InviteCreatedResponse>> create(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID eventId,
            @Valid @RequestBody InviteCreateRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .cacheControl(CacheControl.noStore())
                .body(CustomApiResponse.success(
                        inviteService.create(jwt, eventId, request),
                        "Invite created.",
                        HttpStatus.CREATED.value()
                ));
    }

    @DeleteMapping("/{inviteId}")
    public ResponseEntity<CustomApiResponse<EventInviteResponse>> revoke(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID eventId,
            @PathVariable UUID inviteId
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(CustomApiResponse.success(
                        inviteService.revoke(jwt, eventId, inviteId),
                        "Invite revoked.",
                        HttpStatus.OK.value()
                ));
    }
}