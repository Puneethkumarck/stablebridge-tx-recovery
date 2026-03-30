package com.stablebridge.txrecovery.application.security;

import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(ApiKeyProperties.class)
public class ApiKeyAuthFilterConfig {

    private final ApiKeyProperties apiKeyProperties;

    @Bean
    FilterRegistrationBean<ApiKeyAuthFilter> apiKeyAuthFilterRegistration() {
        var registration = new FilterRegistrationBean<>(new ApiKeyAuthFilter(buildKeyToOperatorMap()));
        registration.addUrlPatterns("/api/v1/*");
        registration.setOrder(1);
        return registration;
    }

    private Map<String, String> buildKeyToOperatorMap() {
        if (!apiKeyProperties.operators().isEmpty()) {
            var keyToOperator = new HashMap<String, String>();
            apiKeyProperties.operators().forEach((operator, key) -> keyToOperator.put(key, operator));
            return Map.copyOf(keyToOperator);
        }
        if (apiKeyProperties.key() != null && !apiKeyProperties.key().isBlank()) {
            return Map.of(apiKeyProperties.key(), "system");
        }
        return Map.of();
    }
}
