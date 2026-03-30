package com.stablebridge.txrecovery.domain.transaction.model;

import lombok.Builder;

@Builder(toBuilder = true)
public record CancellationResult(
        String transactionId,
        TransactionStatus status,
        String message) {}
