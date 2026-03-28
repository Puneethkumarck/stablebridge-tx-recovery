package com.stablebridge.txrecovery.infrastructure.signer;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Builder;

@Builder(toBuilder = true)
@ConfigurationProperties(prefix = "str.signer")
public record LocalSignerProperties(
        String keystorePath,
        String password) {
}
