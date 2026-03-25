package com.stablebridge.txrecovery.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

import lombok.Builder;

@Builder
public record TransactionProjection(
        String transactionId,
        String intentId,
        String chain,
        TransactionStatus status,
        String txHash,
        String fromAddress,
        String toAddress,
        BigDecimal amount,
        String token,
        int retryCount,
        BigDecimal gasSpent,
        Instant submittedAt,
        Instant confirmedAt) {

    public TransactionProjection {
        Objects.requireNonNull(transactionId);
        Objects.requireNonNull(intentId);
        Objects.requireNonNull(chain);
        Objects.requireNonNull(status);
    }
}
