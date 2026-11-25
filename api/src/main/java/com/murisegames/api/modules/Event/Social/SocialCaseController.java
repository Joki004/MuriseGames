package com.murisegames.api.modules.Event.Social;

import com.murisegames.api.modules.Event.Social.SocialCaseTypes.*;
import com.murisegames.api.utils.CustomApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Social scenario cases", description = "Social scenario pool, access requests, and private role cards")
@RestController
@RequestMapping("/api/events/{eventId}/social-cases")
@RequiredArgsConstructor
public class SocialCaseController {

    private final SocialCaseService service;

    @GetMapping
    public ResponseEntity<CustomApiResponse<PoolResponse>> list(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID eventId
    ) {
        return ok(service.list(jwt, eventId), "Social cases retrieved.");
    }

    @PostMapping
    public ResponseEntity<CustomApiResponse<CaseDetail>> create(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID eventId,
            @Valid @RequestBody CreateRequest request
    ) {
        return ok(service.create(jwt, eventId, request), "Social case created.");
    }

    @GetMapping("/{caseId}")
    public ResponseEntity<CustomApiResponse<CaseDetail>> get(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID eventId,
            @PathVariable UUID caseId
    ) {
        return ok(service.get(jwt, eventId, caseId), "Social case retrieved.");
    }

    @PutMapping("/{caseId}")
    public ResponseEntity<CustomApiResponse<CaseDetail>> save(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID eventId,
            @PathVariable UUID caseId,
            @Valid @RequestBody SaveRequest request
    ) {
        return ok(service.save(jwt, eventId, caseId, request), "Social case saved.");
    }

    @DeleteMapping("/{caseId}")
    public ResponseEntity<CustomApiResponse<Void>> archive(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID eventId,
            @PathVariable UUID caseId
    ) {
        service.archive(jwt, eventId, caseId);
        return ok(null, "Social case removed from the pool.");
    }

    @GetMapping("/{caseId}/versions/{versionNumber}")
    public ResponseEntity<CustomApiResponse<VersionDetail>> version(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID eventId,
            @PathVariable UUID caseId,
            @PathVariable int versionNumber
    ) {
        return ok(service.version(jwt, eventId, caseId, versionNumber), "Version retrieved.");
    }

    @PostMapping("/role-assignments")
    public ResponseEntity<CustomApiResponse<RoleAssignmentView>> assignRole(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID eventId,
            @Valid @RequestBody AssignRoleRequest request
    ) {
        return ok(service.assignRole(jwt, eventId, request), "Role assigned.");
    }

    @GetMapping("/role-card")
    public ResponseEntity<CustomApiResponse<RoleCardView>> myRoleCard(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID eventId,
            @RequestParam(defaultValue = "1") int roundNumber
    ) {
        return ok(service.myRoleCard(jwt, eventId, roundNumber), "Role card retrieved.");
    }

    @GetMapping("/access-requests")
    public ResponseEntity<CustomApiResponse<List<AccessRequestView>>> listRequests(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID eventId
    ) {
        return ok(service.listRequests(jwt, eventId), "Access requests retrieved.");
    }

    @PostMapping("/{caseId}/access-requests")
    public ResponseEntity<CustomApiResponse<AccessRequestView>> requestAccess(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID eventId,
            @PathVariable UUID caseId,
            @Valid @RequestBody AccessRequestCreate request
    ) {
        return ok(service.requestAccess(jwt, eventId, caseId, request), "Access requested.");
    }

    @PostMapping("/access-requests/{requestId}/decision")
    public ResponseEntity<CustomApiResponse<AccessRequestView>> decide(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID eventId,
            @PathVariable UUID requestId,
            @Valid @RequestBody AccessDecision decision
    ) {
        return ok(service.decide(jwt, eventId, requestId, decision), "Request answered.");
    }

    private static <T> ResponseEntity<CustomApiResponse<T>> ok(T data, String message) {
        return ResponseEntity.ok(CustomApiResponse.success(data, message, HttpStatus.OK.value()));
    }
}