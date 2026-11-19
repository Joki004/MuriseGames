package com.murisegames.api.config;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class SecurityConfigTests {
    @Test
    void rejectsTokenIntendedForAnotherAudience() {
        var validator = SecurityConfig.audienceValidator("authenticated");
        assertTrue(validator.validate(token("another-api")).hasErrors());
        assertFalse(validator.validate(token("authenticated")).hasErrors());
        assertTrue(validator.validate(Jwt.withTokenValue("test-only").header("alg", "ES256")
            .subject("user-id").build()).hasErrors());
    }

    @Test
    void requiresOneExplicitRuntime() {
        assertThrows(IllegalStateException.class, () -> RuntimeProfileGuard.validate(new String[] {}));
        assertThrows(IllegalStateException.class, () -> RuntimeProfileGuard.validate(new String[] {"local", "prod"}));
        assertDoesNotThrow(() -> RuntimeProfileGuard.validate(new String[] {"prod"}));
    }

    private Jwt token(String audience) {
        return Jwt.withTokenValue("test-only").header("alg", "ES256")
            .subject("user-id").audience(List.of(audience)).build();
    }
}
