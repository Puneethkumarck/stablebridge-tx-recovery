package com.stablebridge.txrecovery.application.security;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    static final String API_KEY_HEADER = "X-API-Key";
    static final String ERROR_CODE = "STR-4010";
    static final String ERROR_MESSAGE = "Unauthorized";
    static final String ERROR_RESPONSE_BODY =
            "{\"error\": \"" + ERROR_CODE + "\", \"message\": \"" + ERROR_MESSAGE + "\"}";

    private final String configuredApiKey;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        var providedKey = request.getHeader(API_KEY_HEADER);

        if (configuredApiKey == null || configuredApiKey.isBlank()) {
            log.warn("API key not configured — rejecting request to {}", request.getRequestURI());
            sendUnauthorized(response);
            return;
        }

        if (providedKey == null || !configuredApiKey.equals(providedKey)) {
            log.warn("Invalid or missing API key for request to {}", request.getRequestURI());
            sendUnauthorized(response);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void sendUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(ERROR_RESPONSE_BODY);
    }
}
