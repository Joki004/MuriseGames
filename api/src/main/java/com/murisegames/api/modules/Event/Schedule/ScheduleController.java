package com.murisegames.api.modules.Event.Schedule;

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

import static com.murisegames.api.modules.Event.Schedule.ScheduleTypes.*;

@RestController
@RequestMapping("/api/v1/events/{eventId}")
@RequiredArgsConstructor
public class ScheduleController {

    private final ScheduleService scheduleService;

    @GetMapping("/schedules")
    public ResponseEntity<CustomApiResponse<List<ScheduleSummary>>> listSchedules(
            @PathVariable UUID eventId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ResponseEntity.ok(CustomApiResponse.success(
                scheduleService.listSchedules(jwt, eventId),
                "Schedules retrieved.",
                HttpStatus.OK.value()
        ));
    }

    @GetMapping("/schedules/{scheduleId}")
    public ResponseEntity<CustomApiResponse<ScheduleDetail>> getSchedule(
            @PathVariable UUID eventId,
            @PathVariable UUID scheduleId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ResponseEntity.ok(CustomApiResponse.success(
                scheduleService.getSchedule(jwt, eventId, scheduleId),
                "Schedule retrieved.",
                HttpStatus.OK.value()
        ));
    }

    @PostMapping("/schedules/manual")
    public ResponseEntity<CustomApiResponse<ScheduleDetail>> createManualDraft(
            @PathVariable UUID eventId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(CustomApiResponse.success(
                scheduleService.createManualDraft(jwt, eventId),
                "Manual draft schedule created.",
                HttpStatus.CREATED.value()
        ));
    }

    @PostMapping("/schedules/balanced")
    public ResponseEntity<CustomApiResponse<ScheduleDetail>> generateBalancedDraft(
            @PathVariable UUID eventId,
            @Valid @RequestBody GenerateBalancedDraftRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(CustomApiResponse.success(
                scheduleService.generateBalancedDraft(jwt, eventId, request),
                "Balanced draft schedule generated.",
                HttpStatus.CREATED.value()
        ));
    }

    @PostMapping("/schedules/{scheduleId}/fixtures")
    public ResponseEntity<CustomApiResponse<ScheduleDetail>> addFixture(
            @PathVariable UUID eventId,
            @PathVariable UUID scheduleId,
            @Valid @RequestBody AddFixtureRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(CustomApiResponse.success(
                scheduleService.addFixture(jwt, eventId, scheduleId, request),
                "Fixture added to draft schedule.",
                HttpStatus.CREATED.value()
        ));
    }

    @DeleteMapping("/schedules/{scheduleId}/fixtures/{fixtureId}")
    public ResponseEntity<CustomApiResponse<ScheduleDetail>> deleteFixture(
            @PathVariable UUID eventId,
            @PathVariable UUID scheduleId,
            @PathVariable UUID fixtureId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ResponseEntity.ok(CustomApiResponse.success(
                scheduleService.deleteFixture(jwt, eventId, scheduleId, fixtureId),
                "Fixture removed from draft schedule.",
                HttpStatus.OK.value()
        ));
    }

    @GetMapping("/resources")
    public ResponseEntity<CustomApiResponse<List<ResourceView>>> listResources(
            @PathVariable UUID eventId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ResponseEntity.ok(CustomApiResponse.success(
                scheduleService.listResources(jwt, eventId),
                "Event resources retrieved.",
                HttpStatus.OK.value()
        ));
    }

    @PostMapping("/resources")
    public ResponseEntity<CustomApiResponse<ResourceView>> createResource(
            @PathVariable UUID eventId,
            @Valid @RequestBody CreateResourceRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(CustomApiResponse.success(
                scheduleService.createResource(jwt, eventId, request),
                "Event resource created.",
                HttpStatus.CREATED.value()
        ));
    }
}