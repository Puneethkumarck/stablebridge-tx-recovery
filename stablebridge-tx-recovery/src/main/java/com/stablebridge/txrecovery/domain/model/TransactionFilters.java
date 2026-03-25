package com.stablebridge.txrecovery.domain.model;

import java.time.Instant;

import lombok.Builder;

@Builder
public record TransactionFilters(
        String chain,
        TransactionStatus status,
        String fromAddress,
        String toAddress,
        Instant fromDate,
        Instant toDate) {
}
