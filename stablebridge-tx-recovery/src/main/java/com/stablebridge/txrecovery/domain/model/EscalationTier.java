package com.stablebridge.txrecovery.domain.model;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Objects;

import lombok.Builder;

@Builder
public record EscalationTier(
        int level,
        Duration stuckThreshold,
        BigDecimal gasMultiplier,
        boolean requiresHumanApproval,
        String description) {

    public EscalationTier {
        Objects.requireNonNull(stuckThreshold);
        Objects.requireNonNull(gasMultiplier);
    }
}
