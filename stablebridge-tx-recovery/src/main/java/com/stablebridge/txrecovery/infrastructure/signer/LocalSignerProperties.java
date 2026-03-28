package com.stablebridge.txrecovery.infrastructure.signer;

import lombok.Builder;

@Builder(toBuilder = true)
public record LocalSignerProperties(
        String keystorePath,
        String password) {
}
