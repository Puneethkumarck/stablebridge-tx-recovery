package com.stablebridge.txrecovery.api.model;

import java.math.BigDecimal;
import java.time.Instant;

import lombok.Builder;

@Builder(toBuilder = true)
public record TransactionResponse(
        String transactionId,
        String intentId,
        String chain,
        String status,
        String txHash,
        String fromAddress,
        String toAddress,
        BigDecimal amount,
        String token,
        int retryCount,
        BigDecimal gasSpent,
        BigDecimal estimatedGasBudget,
        String submissionStrategy,
        Instant submittedAt,
        Instant confirmedAt,
        Instant createdAt) {}
