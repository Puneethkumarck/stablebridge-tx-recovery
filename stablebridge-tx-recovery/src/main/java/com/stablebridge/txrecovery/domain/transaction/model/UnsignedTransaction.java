package com.stablebridge.txrecovery.domain.transaction.model;

import java.util.Map;
import java.util.Objects;

import lombok.Builder;

@Builder(toBuilder = true)
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
        payload = payload.clone();
        metadata = metadata == null ? null : Map.copyOf(metadata);
    }
}
