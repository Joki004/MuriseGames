package com.murisegames.api.modules.User;

import com.murisegames.api.modules.User.dto.UserUpdateRequest;
import com.murisegames.api.utils.CustomApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Users", description = "Authenticated user profile management")
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "Get current user profile")
    @GetMapping("/me")
    public ResponseEntity<CustomApiResponse<UserResponse>> getMe(
            @AuthenticationPrincipal Jwt jwt
    ) {
        UserResponse response = userService.getProfile(jwt);
        return ResponseEntity.ok(
                CustomApiResponse.success(response, "Profile retrieved.", HttpStatus.OK.value())
        );
    }

    @Operation(summary = "Update current user display name")
    @PatchMapping("/me")
    public ResponseEntity<CustomApiResponse<UserResponse>> updateMe(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UserUpdateRequest request
    ) {
        UserResponse response = userService.updateDisplayName(jwt, request);
        return ResponseEntity.ok(
                CustomApiResponse.success(response, "Profile updated.", HttpStatus.OK.value())
        );
    }
}