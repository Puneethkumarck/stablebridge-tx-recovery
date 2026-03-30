package com.stablebridge.txrecovery.application.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

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
    static final String OPERATOR_IDENTITY_ATTRIBUTE = "str.operator.identity";
    static final String ERROR_CODE = "STR-4010";
    static final String ERROR_MESSAGE = "Unauthorized";
    static final String ERROR_RESPONSE_BODY =
            "{\"error\": \"" + ERROR_CODE + "\", \"message\": \"" + ERROR_MESSAGE + "\"}";

    private final Map<String, String> keyToOperator;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        var providedKey = request.getHeader(API_KEY_HEADER);

        if (keyToOperator.isEmpty()) {
            log.warn("API key not configured — rejecting request to {}", request.getRequestURI());
            sendUnauthorized(response);
            return;
        }

        if (providedKey == null) {
            log.warn("Missing API key for request to {}", request.getRequestURI());
            sendUnauthorized(response);
            return;
        }

        var operatorName = findOperator(providedKey);
        if (operatorName == null) {
            log.warn("Invalid API key for request to {}", request.getRequestURI());
            sendUnauthorized(response);
            return;
        }

        request.setAttribute(OPERATOR_IDENTITY_ATTRIBUTE, operatorName);
        filterChain.doFilter(request, response);
    }

    private String findOperator(String providedKey) {
        var providedBytes = providedKey.getBytes(StandardCharsets.UTF_8);
        for (var entry : keyToOperator.entrySet()) {
            var configuredBytes = entry.getKey().getBytes(StandardCharsets.UTF_8);
            if (MessageDigest.isEqual(configuredBytes, providedBytes)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private void sendUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(ERROR_RESPONSE_BODY);
    }
}
