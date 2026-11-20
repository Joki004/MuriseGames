package com.murisegames.api.config;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

@Configuration
@RequiredArgsConstructor
public class JwtValidationConfig {

    private final JwtProperties jwtProperties;

    @Bean
    public JwtDecoder jwtDecoder() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withJwkSetUri(jwtProperties.getJwkSetUri().trim())
                .jwsAlgorithms(algorithms -> {
                    for (String alg : jwtProperties.getJwsAlgorithms()) {
                        SignatureAlgorithm resolved = SignatureAlgorithm.from(alg.trim());
                        if (resolved == null) {
                            throw new IllegalArgumentException("Unsupported JWS algorithm: " + alg);
                        }
                        algorithms.add(resolved);
                    }
                })
                .build();

        decoder.setJwtValidator(buildValidator());
        return decoder;
    }

    OAuth2TokenValidator<Jwt> buildValidator() {
        OAuth2TokenValidator<Jwt> defaultWithIssuer =
                JwtValidators.createDefaultWithIssuer(jwtProperties.getIssuerUri().trim());

        List<String> expectedAudiences = jwtProperties.getAudiences();
        OAuth2TokenValidator<Jwt> audienceValidator = new JwtClaimValidator<List<String>>(
                "aud",
                aud -> aud != null && !Collections.disjoint(aud, expectedAudiences)
        );

        OAuth2TokenValidator<Jwt> subjectUuidValidator = new JwtClaimValidator<String>(
                "sub",
                this::isValidUuid
        );

        return new DelegatingOAuth2TokenValidator<>(
                defaultWithIssuer,
                audienceValidator,
                subjectUuidValidator
        );
    }

    private boolean isValidUuid(String sub) {
        if (sub == null || sub.isBlank()) {
            return false;
        }
        try {
            UUID.fromString(sub);
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }
}