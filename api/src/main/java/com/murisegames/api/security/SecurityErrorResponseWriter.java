package com.murisegames.api.security;

import java.io.IOException;

import com.murisegames.api.utils.CustomApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class SecurityErrorResponseWriter {

    public static final String UNAUTHORIZED_MESSAGE =
            "Authentication is required to access this resource.";
    public static final String ACCESS_DENIED_MESSAGE =
            "You do not have permission to perform this action.";

    private final ObjectMapper objectMapper;

    public void write(
            HttpServletResponse response,
            HttpStatus status,
            String message
    ) throws IOException {
        if (response.isCommitted()) {
            return;
        }

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");

        CustomApiResponse<Void> payload = CustomApiResponse.fail(
                message,
                status.value()
        );

        objectMapper.writeValue(response.getOutputStream(), payload);
    }
}