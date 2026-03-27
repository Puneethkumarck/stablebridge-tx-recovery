package com.stablebridge.txrecovery.domain.transaction.model;

import java.util.Objects;

import lombok.Builder;

@Builder(toBuilder = true)
public record ConfirmationStatus(
        String txHash,
        String chain,
        long confirmations,
        long requiredConfirmations,
        boolean finalized) {

    public ConfirmationStatus {
        Objects.requireNonNull(txHash);
        Objects.requireNonNull(chain);
    }
}
