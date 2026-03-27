package com.stablebridge.txrecovery.domain.transaction.model;

import java.util.Objects;

import lombok.Builder;

@Builder(toBuilder = true)
public record SignedTransaction(
        String intentId,
        String chain,
        byte[] signedPayload,
        String signerAddress) {

    public SignedTransaction {
        Objects.requireNonNull(intentId);
        Objects.requireNonNull(chain);
        Objects.requireNonNull(signedPayload);
        Objects.requireNonNull(signerAddress);
        signedPayload = signedPayload.clone();
    }
}
