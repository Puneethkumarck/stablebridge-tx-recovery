package com.stablebridge.txrecovery.domain.transaction.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import lombok.Builder;

@Builder(toBuilder = true)
public record BatchSubmissionResult(
        String batchId,
        List<TransactionProjection> projections,
        Instant createdAt) {

    public BatchSubmissionResult {
        Objects.requireNonNull(batchId);
        Objects.requireNonNull(projections);
        Objects.requireNonNull(createdAt);
    }
}
