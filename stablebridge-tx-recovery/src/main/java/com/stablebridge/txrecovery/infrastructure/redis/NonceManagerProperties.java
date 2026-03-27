package com.stablebridge.txrecovery.infrastructure.redis;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "str.nonce")
public record NonceManagerProperties(Duration gapDetectionInterval) {

    public NonceManagerProperties {
        if (gapDetectionInterval == null) {
            gapDetectionInterval = Duration.ofSeconds(30);
        }
    }
}
