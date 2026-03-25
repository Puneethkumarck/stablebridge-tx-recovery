package com.stablebridge.txrecovery.domain.model;

import java.math.BigDecimal;
import java.util.Objects;

import lombok.Builder;

@Builder
public record RecoveryResult(
        RecoveryOutcome outcome,
        String replacementTxHash,
        BigDecimal gasCost,
        String details) {

    public RecoveryResult {
        Objects.requireNonNull(outcome);
    }
}
