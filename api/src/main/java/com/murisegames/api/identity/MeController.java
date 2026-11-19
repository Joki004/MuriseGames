package com.murisegames.api.identity;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MeController {
    @GetMapping("/api/me")
    public Identity me(@AuthenticationPrincipal Jwt jwt) {
        // Event roles will come from event memberships, not editable user metadata.
        return new Identity(jwt.getSubject());
    }

    public record Identity(String id) {}
}
