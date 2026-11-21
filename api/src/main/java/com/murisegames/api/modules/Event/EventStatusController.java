package com.murisegames.api.modules.Event;

import com.murisegames.api.modules.Event.Membership.EventMembershipService;
import com.murisegames.api.modules.User.UserService;
import com.murisegames.api.utils.CustomApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@Tag(name = "Events")
@RestController
@RequestMapping("/api/events/{eventId}/status")
@RequiredArgsConstructor
public class EventStatusController {

    private final EventMembershipService membershipService;
    private final UserService userService;
    private final EntityManager entityManager;

    public record EventStatusUpdateRequest(
            @NotBlank
            @Pattern(regexp = "draft|published|live|completed|archived",
                    message = "Status must be draft, published, live, completed, or archived.")
            String status
    ) {
    }

    @Operation(summary = "Update an event's lifecycle status (organizer only)")
    @PatchMapping
    @Transactional
    public ResponseEntity<CustomApiResponse> updateStatus(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID eventId,
            @Valid @RequestBody EventStatusUpdateRequest request
    ) {
        UUID actorId = userService.extractUserId(jwt);
        membershipService.requireOrganizer(eventId, actorId);

        int updated = entityManager.createNativeQuery("""
                UPDATE murise.events
                SET status = :status
                WHERE id = :eventId
                """)
                .setParameter("status", request.status())
                .setParameter("eventId", eventId)
                .executeUpdate();

        if (updated == 0) {
            throw new EntityNotFoundException("Event not found.");
        }

        return ResponseEntity.ok(
                CustomApiResponse.success(
                        Map.of("eventId", eventId, "status", request.status()),
                        "Event status updated.",
                        HttpStatus.OK.value()
                )
        );
    }
}