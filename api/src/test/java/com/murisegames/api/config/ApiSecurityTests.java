package com.murisegames.api.config;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.murisegames.api.identity.MeController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(MeController.class)
@ActiveProfiles("test")
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
    "app.auth.issuer=https://example.invalid/auth/v1",
    "app.auth.jwk-set-uri=https://example.invalid/auth/v1/.well-known/jwks.json",
    "app.auth.audience=authenticated",
    "app.cors.allowed-origins=http://localhost:3000"
})
class ApiSecurityTests {
    @Autowired MockMvc mvc;
    @MockitoBean JwtDecoder decoder;

    @Test
    void requiresLoginAndReturnsTheAuthenticatedIdentity() throws Exception {
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me").with(jwt().jwt(token -> token.subject("user-123"))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.id").value("user-123"));
    }

    @Test
    void deniesUnimplementedCommandsEvenWithAValidIdentity() throws Exception {
        mvc.perform(post("/api/events").with(jwt())).andExpect(status().isForbidden());
    }

    @Test
    void allowsOnlyConfiguredBrowserOrigins() throws Exception {
        mvc.perform(options("/api/me").header("Origin", "http://localhost:3000")
            .header("Access-Control-Request-Method", "GET")
            .header("Access-Control-Request-Headers", "Authorization"))
            .andExpect(status().isOk())
            .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"));
        mvc.perform(options("/api/me").header("Origin", "https://untrusted.example")
            .header("Access-Control-Request-Method", "GET"))
            .andExpect(status().isForbidden());
    }
}
