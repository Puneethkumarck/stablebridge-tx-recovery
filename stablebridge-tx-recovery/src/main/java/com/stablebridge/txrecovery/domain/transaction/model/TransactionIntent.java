package com.stablebridge.txrecovery.domain.transaction.model;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

import lombok.Builder;

@Builder(toBuilder = true)
public record TransactionIntent(
        String intentId,
        String chain,
        String toAddress,
        BigDecimal amount,
        String token,
        int tokenDecimals,
        BigInteger rawAmount,
        String tokenContractAddress,
        SubmissionStrategy strategy,
        Map<String, String> metadata,
        String batchId,
        Instant createdAt) {

    public TransactionIntent {
        Objects.requireNonNull(intentId);
        Objects.requireNonNull(chain);
        Objects.requireNonNull(toAddress);
        Objects.requireNonNull(amount);
        Objects.requireNonNull(token);
        metadata = metadata == null ? null : Map.copyOf(metadata);
    }
}
