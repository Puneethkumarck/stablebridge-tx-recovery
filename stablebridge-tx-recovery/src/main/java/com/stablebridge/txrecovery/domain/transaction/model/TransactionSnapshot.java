package com.stablebridge.txrecovery.domain.transaction.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

import com.stablebridge.txrecovery.domain.recovery.model.EscalationTier;

import lombok.Builder;

@Builder(toBuilder = true)
public record TransactionSnapshot(
        String transactionId,
        String intentId,
        TransactionStatus status,
        String txHash,
        int retryCount,
        BigDecimal gasSpent,
        EscalationTier currentTier,
        Instant updatedAt) {

    public TransactionSnapshot {
        Objects.requireNonNull(transactionId);
        Objects.requireNonNull(intentId);
        Objects.requireNonNull(status);
    }
}
