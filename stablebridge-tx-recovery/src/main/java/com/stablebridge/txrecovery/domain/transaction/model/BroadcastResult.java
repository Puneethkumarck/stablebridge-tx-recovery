package com.stablebridge.txrecovery.domain.transaction.model;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

import lombok.Builder;

@Builder(toBuilder = true)
public record BroadcastResult(
        String txHash,
        String chain,
        Instant broadcastedAt,
        Map<String, String> details) {

    public BroadcastResult {
        Objects.requireNonNull(txHash);
        Objects.requireNonNull(chain);
        Objects.requireNonNull(broadcastedAt);
        details = details == null ? null : Map.copyOf(details);
    }
}
