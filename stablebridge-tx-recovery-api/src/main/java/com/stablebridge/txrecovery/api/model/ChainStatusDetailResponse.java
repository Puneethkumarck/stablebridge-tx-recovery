package com.stablebridge.txrecovery.api.model;

import lombok.Builder;

@Builder(toBuilder = true)
public record ChainStatusDetailResponse(
        String chain,
        boolean healthy,
        long pendingCount,
        long stuckCount,
        long rpcLatencyMs,
        String status,
        AddressPoolStats addressPool,
        NonceHealthStats nonceHealth) {

    @Builder(toBuilder = true)
    public record AddressPoolStats(
            long total,
            long active,
            long draining) {}

    @Builder(toBuilder = true)
    public record NonceHealthStats(
            long gapCount,
            long inFlightTotal) {}
}
