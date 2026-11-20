package com.murisegames.api.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "app.validation.runtime-config.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class RuntimeConfigurationValidator {

    private final JwtProperties jwtProperties;

    @PostConstruct
    public void validate() {
        if (jwtProperties.getIssuerUri() == null || jwtProperties.getIssuerUri().isBlank()) {
            throw new IllegalStateException("JWT issuer-uri must be configured");
        }
        if (jwtProperties.getJwkSetUri() == null || jwtProperties.getJwkSetUri().isBlank()) {
            throw new IllegalStateException("JWT jwk-set-uri must be configured");
        }
        if (jwtProperties.getAudiences() == null || jwtProperties.getAudiences().isEmpty()) {
            throw new IllegalStateException("At least one JWT audience must be configured");
        }
        if (jwtProperties.getJwsAlgorithms() == null || jwtProperties.getJwsAlgorithms().isEmpty()) {
            throw new IllegalStateException("At least one JWT JWS algorithm must be configured");
        }
    }
}