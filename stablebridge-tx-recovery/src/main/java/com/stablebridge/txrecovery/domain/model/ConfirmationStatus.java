package com.stablebridge.txrecovery.domain.model;

import java.util.Objects;

import lombok.Builder;

@Builder
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
