package com.murisegames.api.modules.Event.Invite;

import com.murisegames.api.modules.Event.Invite.dto.InviteRedeemRequest;
import com.murisegames.api.modules.User.UserService;
import com.murisegames.api.utils.CustomApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/invites")
@RequiredArgsConstructor
public class InviteRedemptionController {

    private final InviteRedemptionService redemptionService;
    private final InviteRedemptionLimiter limiter;
    private final UserService userService;

    @PostMapping("/redeem")
    public ResponseEntity<CustomApiResponse<InviteRedeemResponse>> redeem(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody InviteRedeemRequest request
    ) {
        // Runs before the redemption transaction.
        if (!limiter.allow(userService.extractUserId(jwt))) {
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Too many attempts. Try again in one minute."
            );
        }

        InviteRedeemResponse result =
                redemptionService.redeem(jwt, request.token());

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(CustomApiResponse.success(
                        result,
                        "Invitation processed.",
                        HttpStatus.OK.value()
                ));
    }
}