package com.stablebridge.txrecovery.application.config;

import java.util.Objects;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "str.redis")
public record RedisProperties(String host, int port) {

    public RedisProperties {
        Objects.requireNonNull(host, "str.redis.host must be configured");
        if (port <= 0) {
            port = 6379;
        }
    }
}
