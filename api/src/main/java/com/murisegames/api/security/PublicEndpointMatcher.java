package com.murisegames.api.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Single source of truth for routes that do not require Supabase JWT
 * authentication.
 *
 * <p>Used by {@code SecurityConfig} to permit public routes while keeping
 * Swagger endpoints closed unless {@code app.swagger.enabled=true}.</p>
 */
@Component
public final class PublicEndpointMatcher implements RequestMatcher {

    private final RequestMatcher delegate;

    public PublicEndpointMatcher(
            @Value("${app.swagger.enabled:false}") boolean swaggerEnabled
    ) {
        PathPatternRequestMatcher.Builder paths =
                PathPatternRequestMatcher.withDefaults();

        List<RequestMatcher> matchers = new ArrayList<>(List.of(
                paths.matcher(HttpMethod.OPTIONS, "/**"),
                paths.matcher(HttpMethod.GET, "/actuator/health")
        ));

        if (swaggerEnabled) {
            matchers.add(paths.matcher(HttpMethod.GET, "/swagger-ui.html"));
            matchers.add(paths.matcher(HttpMethod.GET, "/swagger-ui/**"));
            matchers.add(paths.matcher(HttpMethod.GET, "/v3/api-docs"));
            matchers.add(paths.matcher(HttpMethod.GET, "/v3/api-docs/**"));
        }

        this.delegate = new OrRequestMatcher(matchers);
    }

    @Override
    public boolean matches(HttpServletRequest request) {
        return delegate.matches(request);
    }
}