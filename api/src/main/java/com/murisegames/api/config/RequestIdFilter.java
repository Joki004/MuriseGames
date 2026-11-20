package com.murisegames.api.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-ID";
    public static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    public static final String MDC_REQUEST_ID_KEY = "requestId";
    public static final String REQUEST_ID_ATTRIBUTE =
            RequestIdFilter.class.getName() + ".requestId";

    private static final int MAX_REQUEST_ID_LENGTH = 100;

    private static final Pattern SAFE_REQUEST_ID = Pattern.compile(
            "^[A-Za-z0-9][A-Za-z0-9._:-]{0,99}$"
    );

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String requestId = resolveRequestId(request);

        request.setAttribute(REQUEST_ID_ATTRIBUTE, requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);
        MDC.put(MDC_REQUEST_ID_KEY, requestId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_REQUEST_ID_KEY);
        }
    }

    private String resolveRequestId(HttpServletRequest request) {
        Object existingRequestId = request.getAttribute(REQUEST_ID_ATTRIBUTE);

        if (existingRequestId instanceof String existing
                && isSafeRequestId(existing)) {
            return existing;
        }

        String requestId = normalizedHeader(request.getHeader(REQUEST_ID_HEADER));
        if (isSafeRequestId(requestId)) {
            return requestId;
        }

        String correlationId = normalizedHeader(request.getHeader(CORRELATION_ID_HEADER));
        if (isSafeRequestId(correlationId)) {
            return correlationId;
        }

        return UUID.randomUUID().toString();
    }

    private String normalizedHeader(String headerValue) {
        if (headerValue == null) {
            return null;
        }

        String normalized = headerValue.trim();
        if (normalized.isEmpty() || normalized.length() > MAX_REQUEST_ID_LENGTH) {
            return null;
        }

        return normalized;
    }

    private boolean isSafeRequestId(String requestId) {
        return requestId != null && SAFE_REQUEST_ID.matcher(requestId).matches();
    }
}