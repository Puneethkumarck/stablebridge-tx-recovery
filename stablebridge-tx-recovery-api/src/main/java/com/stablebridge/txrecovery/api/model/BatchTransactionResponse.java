package com.stablebridge.txrecovery.api.model;

import java.time.Instant;
import java.util.List;

import lombok.Builder;

@Builder(toBuilder = true)
public record BatchTransactionResponse(
        String batchId,
        List<TransactionResponse> transactions,
        Instant createdAt) {}
