package com.stablebridge.txrecovery.application.security;

import java.util.Objects;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "str.api")
public record ApiKeyProperties(String key) {

    public ApiKeyProperties {
        key = Objects.requireNonNullElse(key, "");
    }
}
