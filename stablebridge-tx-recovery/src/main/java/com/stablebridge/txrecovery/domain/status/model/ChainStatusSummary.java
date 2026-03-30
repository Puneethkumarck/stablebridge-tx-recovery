package com.stablebridge.txrecovery.domain.status.model;

import lombok.Builder;

@Builder(toBuilder = true)
public record ChainStatusSummary(
        String chain,
        boolean healthy,
        long pendingCount,
        long stuckCount,
        long avgConfirmationMs,
        long lastBlockSeen,
        long rpcLatencyMs,
        HealthStatus status) {}
