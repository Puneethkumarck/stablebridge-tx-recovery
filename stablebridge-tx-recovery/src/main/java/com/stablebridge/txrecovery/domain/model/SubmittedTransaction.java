package com.stablebridge.txrecovery.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

import lombok.Builder;

@Builder(toBuilder = true)
public record SubmittedTransaction(
        String transactionId,
        String intentId,
        String chain,
        String txHash,
        String fromAddress,
        SubmissionResource resource,
        TransactionStatus status,
        int retryCount,
        BigDecimal gasSpent,
        String gasDenomination,
        BigDecimal gasBudget,
        EscalationTier currentTier,
        List<RecoveryAttempt> recoveryHistory,
        Instant submittedAt,
        Instant stuckSince,
        Instant confirmedAt) {

    public SubmittedTransaction {
        Objects.requireNonNull(transactionId);
        Objects.requireNonNull(intentId);
        Objects.requireNonNull(chain);
        Objects.requireNonNull(status);
    }
}
