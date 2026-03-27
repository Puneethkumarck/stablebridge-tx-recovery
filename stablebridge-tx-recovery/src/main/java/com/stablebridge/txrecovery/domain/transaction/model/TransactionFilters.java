package com.stablebridge.txrecovery.domain.transaction.model;

import java.time.Instant;

import lombok.Builder;

@Builder(toBuilder = true)
public record TransactionFilters(
        String chain,
        TransactionStatus status,
        String fromAddress,
        String toAddress,
        Instant fromDate,
        Instant toDate) {
}
