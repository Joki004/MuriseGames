package com.murisegames.api.modules.Event.Alibi;

import com.murisegames.api.modules.Event.Alibi.AlibiCaseTypes.*;
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
import java.util.Locale;
import java.util.UUID;

@Tag(name = "Alibi cases", description = "Unified Alibi case pool and access requests")
@RestController
@RequestMapping("/api/events/{eventId}/alibi-cases")
@RequiredArgsConstructor
public class AlibiCaseController {

    private final AlibiCaseService service;

    @GetMapping
    public ResponseEntity<CustomApiResponse<PoolResponse>> list(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID eventId
    ) {
        return ok(service.list(jwt, eventId), "Alibi cases retrieved.");
    }

    @PostMapping
    public ResponseEntity<CustomApiResponse<CaseDetail>> create(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID eventId,
            @Valid @RequestBody CreateRequest request
    ) {
        return ok(service.create(jwt, eventId, request), "Alibi case created.");
    }

    @GetMapping("/{caseId}")
    public ResponseEntity<CustomApiResponse<CaseDetail>> get(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID eventId,
            @PathVariable UUID caseId
    ) {
        return ok(service.get(jwt, eventId, caseId), "Alibi case retrieved.");
    }

    @PutMapping("/{caseId}")
    public ResponseEntity<CustomApiResponse<CaseDetail>> save(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID eventId,
            @PathVariable UUID caseId,
            @Valid @RequestBody SaveRequest request
    ) {
        return ok(service.save(jwt, eventId, caseId, request), "Alibi case saved.");
    }

    @DeleteMapping("/{caseId}")
    public ResponseEntity<CustomApiResponse<Void>> archive(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID eventId,
            @PathVariable UUID caseId
    ) {
        service.archive(jwt, eventId, caseId);
        return ok(null, "Alibi case removed from the pool.");
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

    /** role = audience | pair | judge; version defaults to the current one. */
    @GetMapping("/{caseId}/preview")
    public ResponseEntity<CustomApiResponse<RolePreview>> preview(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID eventId,
            @PathVariable UUID caseId,
            @RequestParam String role,
            @RequestParam(required = false) Integer version
    ) {
        PreviewRole parsed;
        try {
            parsed = PreviewRole.valueOf(role.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Role must be audience, pair or judge.");
        }
        return ok(service.preview(jwt, eventId, caseId, parsed, version), "Preview retrieved.");
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
