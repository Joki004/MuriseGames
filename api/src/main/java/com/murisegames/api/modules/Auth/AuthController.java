package com.murisegames.api.modules.Auth;

import com.murisegames.api.utils.CustomApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Auth", description = "Supabase JWT verification and account resolution")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "Resolve authenticated Supabase account and DB-backed roles")
    @GetMapping("/me")
    public ResponseEntity<CustomApiResponse<AuthResponse>> me(
            @AuthenticationPrincipal Jwt jwt
    ) {
        AuthResponse response = authService.resolveAuthenticatedAccount(jwt);
        return ResponseEntity.ok(
                CustomApiResponse.success(
                        response,
                        "Authenticated account resolved.",
                        HttpStatus.OK.value()
                )
        );
    }
}