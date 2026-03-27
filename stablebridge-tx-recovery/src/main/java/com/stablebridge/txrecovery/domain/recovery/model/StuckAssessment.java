package com.stablebridge.txrecovery.domain.recovery.model;

import java.util.Objects;

import lombok.Builder;

@Builder(toBuilder = true)
public record StuckAssessment(
        StuckReason reason,
        StuckSeverity severity,
        RecoveryPlan recommendedPlan,
        String explanation) {

    public StuckAssessment {
        Objects.requireNonNull(reason);
        Objects.requireNonNull(severity);
        Objects.requireNonNull(recommendedPlan);
    }
}
