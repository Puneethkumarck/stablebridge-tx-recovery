package com.stablebridge.txrecovery.domain.status.model;

import lombok.Builder;

@Builder(toBuilder = true)
public record ChainStatusDetail(
        String chain,
        boolean healthy,
        long pendingCount,
        long stuckCount,
        long rpcLatencyMs,
        HealthStatus status,
        long addressPoolTotal,
        long addressPoolActive,
        long addressPoolDraining,
        long nonceGapCount,
        long nonceInFlightTotal) {}
