package com.stablebridge.txrecovery.domain.recovery.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

import lombok.Builder;

@Builder(toBuilder = true)
public record RecoveryAttempt(
        int attemptNumber,
        RecoveryAction action,
        String originalTxHash,
        String replacementTxHash,
        FeeEstimate feeUsed,
        BigDecimal gasCost,
        Instant attemptedAt,
        RecoveryOutcome outcome) {

    public RecoveryAttempt {
        Objects.requireNonNull(action);
        Objects.requireNonNull(originalTxHash);
        Objects.requireNonNull(outcome);
    }
}
