package com.murisegames.api.modules.Event;

import com.murisegames.api.modules.Event.dto.EventCreateRequest;
import com.murisegames.api.utils.CustomApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Events", description = "Event creation and membership-scoped listing")
@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;

    @Operation(summary = "Create an event and become its first organizer")
    @PostMapping
    public ResponseEntity<CustomApiResponse<EventResponse>> create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody EventCreateRequest request
    ) {
        EventResponse response = eventService.create(jwt, request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(CustomApiResponse.success(
                        response,
                        "Event created.",
                        HttpStatus.CREATED.value()
                ));
    }

    @Operation(summary = "List events accessible through current membership")
    @GetMapping
    public ResponseEntity<CustomApiResponse<Page<EventResponse>>> listMine(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Page<EventResponse> response = eventService.listMine(jwt, page, size);

        return ResponseEntity.ok(CustomApiResponse.success(
                response,
                "Events retrieved.",
                HttpStatus.OK.value()
        ));
    }
}