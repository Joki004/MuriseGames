package com.murisegames.api.modules.Event.Operations;

import com.murisegames.api.utils.CustomApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

import static com.murisegames.api.modules.Event.Operations.EventOperationsTypes.*;

@Tag(name = "Event Operations", description = "Offline score sheet sync and printable event packets")
@RestController
@RequestMapping("/api/events/{eventId}")
@RequiredArgsConstructor
public class EventOperationsController {

    private final EventOperationsService operationsService;

    @Operation(summary = "Sync offline manual score sheet rows idempotently")
    @PostMapping("/offline-sync")
    public ResponseEntity<CustomApiResponse<OfflineSyncResponse>> syncOfflineRows(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID eventId,
            @Valid @RequestBody OfflineSyncRequest request
    ) {
        OfflineSyncResponse response = operationsService.syncOfflineRows(jwt, eventId, request);
        return ResponseEntity.ok(
                CustomApiResponse.success(
                        response,
                        "Offline score rows processed.",
                        200
                )
        );
    }

    @Operation(summary = "Get printable schedule, score sheet, and clue packet")
    @GetMapping("/print-packet")
    public ResponseEntity<CustomApiResponse<PrintPacketResponse>> getPrintPacket(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID eventId,
            @RequestParam(defaultValue = "public") String scope,
            @RequestParam(defaultValue = "false") boolean includeSecrets
    ) {
        PrintPacketResponse response = operationsService.buildPrintPacket(
                jwt,
                eventId,
                scope,
                includeSecrets
        );
        return ResponseEntity.ok(
                CustomApiResponse.success(
                        response,
                        "Print packet generated.",
                        200
                )
        );
    }
}