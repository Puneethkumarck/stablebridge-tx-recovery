package com.stablebridge.txrecovery.domain.status.model;

import lombok.Builder;

@Builder(toBuilder = true)
public record ChainStatusSummary(
        String chain,
        boolean healthy,
        long pendingCount,
        long stuckCount,
        long rpcLatencyMs,
        HealthStatus status) {}
