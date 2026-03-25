package com.stablebridge.txrecovery.domain.model;

import java.util.Map;
import java.util.Objects;

import lombok.Builder;

@Builder
public record UnsignedTransaction(
        String intentId,
        String chain,
        String fromAddress,
        String toAddress,
        byte[] payload,
        Map<String, String> metadata) {

    public UnsignedTransaction {
        Objects.requireNonNull(intentId);
        Objects.requireNonNull(chain);
        Objects.requireNonNull(fromAddress);
        Objects.requireNonNull(toAddress);
        Objects.requireNonNull(payload);
    }
}
