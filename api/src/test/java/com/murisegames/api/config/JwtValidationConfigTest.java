package com.murisegames.api.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtValidationConfigTest {

    private static final String ISSUER = "https://example.supabase.co/auth/v1";

    private OAuth2TokenValidator<Jwt> validator;

    @BeforeEach
    void setUp() {
        JwtProperties props = new JwtProperties();
        props.setIssuerUri(ISSUER);
        props.setJwkSetUri(ISSUER + "/.well-known/jwks.json");
        props.setAudiences(List.of("authenticated"));
        props.setJwsAlgorithms(List.of("ES256", "RS256"));

        validator = new JwtValidationConfig(props).buildValidator();
    }

    @Test
    void acceptsValidSupabaseJwt() {
        Jwt jwt = baseJwt()
                .subject(UUID.randomUUID().toString())
                .issuer(ISSUER)
                .audience(List.of("authenticated"))
                .expiresAt(Instant.now().plusSeconds(300))
                .build();

        assertThat(validator.validate(jwt).hasErrors()).isFalse();
    }

    @Test
    void rejectsExpiredJwt() {
        Jwt jwt = baseJwt()
                .subject(UUID.randomUUID().toString())
                .issuer(ISSUER)
                .audience(List.of("authenticated"))
                .issuedAt(Instant.now().minusSeconds(600))
                .expiresAt(Instant.now().minusSeconds(120))
                .build();

        assertThat(validator.validate(jwt).hasErrors()).isTrue();
    }

    @Test
    void rejectsWrongIssuer() {
        Jwt jwt = baseJwt()
                .subject(UUID.randomUUID().toString())
                .issuer("https://other.supabase.co/auth/v1")
                .audience(List.of("authenticated"))
                .expiresAt(Instant.now().plusSeconds(300))
                .build();

        assertThat(validator.validate(jwt).hasErrors()).isTrue();
    }

    @Test
    void rejectsWrongAudience() {
        Jwt jwt = baseJwt()
                .subject(UUID.randomUUID().toString())
                .issuer(ISSUER)
                .audience(List.of("anon"))
                .expiresAt(Instant.now().plusSeconds(300))
                .build();

        assertThat(validator.validate(jwt).hasErrors()).isTrue();
    }

    @Test
    void rejectsNonUuidSubject() {
        Jwt jwt = baseJwt()
                .subject("not-a-uuid")
                .issuer(ISSUER)
                .audience(List.of("authenticated"))
                .expiresAt(Instant.now().plusSeconds(300))
                .build();

        assertThat(validator.validate(jwt).hasErrors()).isTrue();
    }

    private Jwt.Builder baseJwt() {
        return Jwt.withTokenValue("test-token")
                .header("alg", "ES256")
                .issuedAt(Instant.now().minusSeconds(10));
    }
}