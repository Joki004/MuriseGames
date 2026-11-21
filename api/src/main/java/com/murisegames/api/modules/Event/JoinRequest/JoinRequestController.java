package com.murisegames.api.modules.Event.JoinRequest;

import com.murisegames.api.modules.Event.JoinRequest.dto.JoinRequestApproveRequest;
import com.murisegames.api.utils.CustomApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Tag(name = "Join Requests", description = "Private event waiting-room approvals")
@RestController
@RequestMapping("/api/events/{eventId}/join-requests")
@RequiredArgsConstructor
public class JoinRequestController {

    private final JoinRequestService joinRequestService;

    @Operation(summary = "List join requests for an event (organizer only)")
    @GetMapping
    public ResponseEntity<CustomApiResponse<List<JoinRequestResponse>>> list(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID eventId,
            @RequestParam(required = false) String status
    ) {
        List<JoinRequestResponse> requests =
                joinRequestService.listRequests(jwt, eventId, status);

        return ResponseEntity.ok(
                CustomApiResponse.success(
                        requests,
                        "Join requests retrieved.",
                        HttpStatus.OK.value()
                )
        );
    }

    @Operation(summary = "Approve a join request and optionally assign a team")
    @PostMapping("/{requestId}/approve")
    public ResponseEntity<CustomApiResponse<JoinRequestResponse>> approve(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID eventId,
            @PathVariable UUID requestId,
            @RequestBody(required = false) JoinRequestApproveRequest request
    ) {
        UUID teamId = request != null ? request.teamId() : null;
        JoinRequestResponse approved =
                joinRequestService.approve(jwt, eventId, requestId, teamId);

        return ResponseEntity.ok(
                CustomApiResponse.success(
                        approved,
                        "Join request approved.",
                        HttpStatus.OK.value()
                )
        );
    }

    @Operation(summary = "Reject a pending join request")
    @PostMapping("/{requestId}/reject")
    public ResponseEntity<CustomApiResponse<JoinRequestResponse>> reject(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID eventId,
            @PathVariable UUID requestId
    ) {
        JoinRequestResponse rejected =
                joinRequestService.reject(jwt, eventId, requestId);

        return ResponseEntity.ok(
                CustomApiResponse.success(
                        rejected,
                        "Join request rejected.",
                        HttpStatus.OK.value()
                )
        );
    }
}