package com.stablebridge.txrecovery.application.security;

import java.util.Map;
import java.util.Objects;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "str.api")
public record ApiKeyProperties(String key, Map<String, String> operators) {

    public ApiKeyProperties {
        key = Objects.requireNonNullElse(key, "");
        operators = Objects.requireNonNullElse(operators, Map.of());
    }
}
