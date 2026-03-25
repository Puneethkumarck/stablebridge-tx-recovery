package com.stablebridge.txrecovery.domain.model;

import java.util.Objects;

import lombok.Builder;

@Builder
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
