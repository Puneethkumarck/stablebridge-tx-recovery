package com.stablebridge.txrecovery.domain.recovery.model;

import java.math.BigDecimal;
import java.util.Objects;

import lombok.Builder;

@Builder(toBuilder = true)
public record RecoveryResult(
        RecoveryOutcome outcome,
        String replacementTxHash,
        BigDecimal gasCost,
        String details) {

    public RecoveryResult {
        Objects.requireNonNull(outcome);
    }
}
