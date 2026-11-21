package com.murisegames.api.modules.Event.Membership;

import com.murisegames.api.utils.CustomApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/events/{eventId}/roles")
@RequiredArgsConstructor
public class EventRoleController {

    private final EventRoleService eventRoleService;

    @GetMapping
    public ResponseEntity<CustomApiResponse<List<EventMemberRolesResponse>>> list(
            @PathVariable UUID eventId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        UUID actorId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(
                CustomApiResponse.success(
                        eventRoleService.listMemberRoles(eventId, actorId),
                        "Event roles retrieved.",
                        HttpStatus.OK.value()
                )
        );
    }

    @PostMapping
    public ResponseEntity<CustomApiResponse<EventMemberRolesResponse>> grant(
            @PathVariable UUID eventId,
            @Valid @RequestBody EventRoleGrantRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        UUID actorId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(
                CustomApiResponse.success(
                        eventRoleService.grantRole(
                                eventId,
                                request.userId(),
                                request.role(),
                                actorId
                        ),
                        "Event role granted.",
                        HttpStatus.OK.value()
                )
        );
    }

    @DeleteMapping("/{userId}/{role}")
    public ResponseEntity<CustomApiResponse<EventMemberRolesResponse>> revoke(
            @PathVariable UUID eventId,
            @PathVariable UUID userId,
            @PathVariable String role,
            @AuthenticationPrincipal Jwt jwt
    ) {
        UUID actorId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(
                CustomApiResponse.success(
                        eventRoleService.revokeRole(eventId, userId, role, actorId),
                        "Event role revoked.",
                        HttpStatus.OK.value()
                )
        );
    }
}