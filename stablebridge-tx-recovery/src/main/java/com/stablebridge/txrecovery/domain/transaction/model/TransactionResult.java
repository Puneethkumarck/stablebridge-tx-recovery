package com.stablebridge.txrecovery.domain.transaction.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

import lombok.Builder;

@Builder(toBuilder = true)
public record TransactionResult(
        String transactionId,
        String intentId,
        TransactionStatus finalStatus,
        String txHash,
        BigDecimal totalGasSpent,
        String gasDenomination,
        int totalAttempts,
        Instant completedAt) {

    public TransactionResult {
        Objects.requireNonNull(transactionId);
        Objects.requireNonNull(intentId);
        Objects.requireNonNull(finalStatus);
    }
}
