package com.stablebridge.txrecovery.api.model;

import lombok.Builder;

@Builder(toBuilder = true)
public record ChainStatusSummaryResponse(
        String chain,
        boolean healthy,
        long pendingCount,
        long stuckCount,
        long avgConfirmationMs,
        long lastBlockSeen,
        Long rpcLatencyMs,
        String status) {}
