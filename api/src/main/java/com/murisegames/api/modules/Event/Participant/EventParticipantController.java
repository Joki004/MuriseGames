package com.murisegames.api.modules.Event.Participant;

import com.murisegames.api.modules.Event.Participant.dto.ParticipantTeamAssignRequest;
import com.murisegames.api.utils.CustomApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Tag(name = "Event Participants", description = "Event roster and team assignments")
@RestController
@RequestMapping("/api/events/{eventId}/participants")
@RequiredArgsConstructor
public class EventParticipantController {

    private final EventParticipantService participantService;

    @Operation(summary = "List event participants")
    @GetMapping
    public ResponseEntity<CustomApiResponse<List<EventParticipantResponse>>> list(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID eventId
    ) {
        List<EventParticipantResponse> participants =
                participantService.listParticipants(jwt, eventId);

        return ResponseEntity.ok(
                CustomApiResponse.success(
                        participants,
                        "Participants retrieved.",
                        HttpStatus.OK.value()
                )
        );
    }

    @Operation(summary = "Assign or move a participant to a team")
    @PatchMapping("/{participantId}/team")
    public ResponseEntity<CustomApiResponse<EventParticipantResponse>> assignTeam(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID eventId,
            @PathVariable UUID participantId,
            @RequestBody ParticipantTeamAssignRequest request
    ) {
        EventParticipantResponse updated = participantService.assignTeam(
                jwt,
                eventId,
                participantId,
                request.teamId()
        );

        return ResponseEntity.ok(
                CustomApiResponse.success(
                        updated,
                        "Participant team updated.",
                        HttpStatus.OK.value()
                )
        );
    }

    @Operation(summary = "Appoint or remove a team captain")
    @PatchMapping("/{participantId}/captain")
    public ResponseEntity<CustomApiResponse<EventParticipantResponse>> setCaptain(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID eventId,
            @PathVariable UUID participantId,
            @RequestBody com.murisegames.api.modules.Event.Participant.dto.ParticipantCaptainRequest request
    ) {
        EventParticipantResponse updated = participantService.setCaptain(
                jwt,
                eventId,
                participantId,
                request.captain()
        );

        return ResponseEntity.ok(
                CustomApiResponse.success(
                        updated,
                        request.captain() ? "Team captain appointed." : "Team captain removed.",
                        HttpStatus.OK.value()
                )
        );
    }
}