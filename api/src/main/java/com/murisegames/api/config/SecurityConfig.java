package com.murisegames.api.config;

import com.murisegames.api.security.PublicEndpointMatcher;
import com.murisegames.api.security.SecurityErrorResponseWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

import static com.murisegames.api.security.SecurityErrorResponseWriter.ACCESS_DENIED_MESSAGE;
import static com.murisegames.api.security.SecurityErrorResponseWriter.UNAUTHORIZED_MESSAGE;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final PublicEndpointMatcher publicEndpointMatcher;
    private final SecurityErrorResponseWriter errorResponseWriter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                // Authentication uses bearer headers, not browser cookies.
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .cors(Customizer.withDefaults())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, authException) ->
                                errorResponseWriter.write(
                                        response,
                                        HttpStatus.UNAUTHORIZED,
                                        UNAUTHORIZED_MESSAGE
                                ))
                        .accessDeniedHandler((request, response, accessDeniedException) ->
                                errorResponseWriter.write(
                                        response,
                                        HttpStatus.FORBIDDEN,
                                        ACCESS_DENIED_MESSAGE
                                )))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(publicEndpointMatcher).permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().denyAll())
                .oauth2ResourceServer(oauth -> oauth
                        .authenticationEntryPoint((request, response, authException) ->
                                errorResponseWriter.write(
                                        response,
                                        HttpStatus.UNAUTHORIZED,
                                        UNAUTHORIZED_MESSAGE
                                ))
                        .jwt(Customizer.withDefaults()))
                .build();
    }
}