package com.stablebridge.txrecovery.infrastructure.signer;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Builder;

@Builder(toBuilder = true)
@ConfigurationProperties(prefix = "str.signer.callback")
public record CallbackSignerProperties(
        String hmacSecret,
        Duration timeout,
        TlsProperties tls) {

    @Builder(toBuilder = true)
    public record TlsProperties(boolean verify) {}
}
