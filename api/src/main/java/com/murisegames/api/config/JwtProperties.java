package com.murisegames.api.config;

import java.util.List;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "spring.security.oauth2.resourceserver.jwt")
public class JwtProperties {

    private String issuerUri;
    private String jwkSetUri;
    private List<String> audiences = List.of();
    private List<String> jwsAlgorithms = List.of();
}