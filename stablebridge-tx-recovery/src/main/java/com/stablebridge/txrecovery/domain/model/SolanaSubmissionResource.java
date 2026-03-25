package com.stablebridge.txrecovery.domain.model;

import java.util.Objects;

import lombok.Builder;

@Builder
public record SolanaSubmissionResource(
        String chain,
        String fromAddress,
        String nonceAccountAddress,
        String nonceValue) implements SubmissionResource {

    public SolanaSubmissionResource {
        Objects.requireNonNull(chain);
        Objects.requireNonNull(fromAddress);
        Objects.requireNonNull(nonceAccountAddress);
        Objects.requireNonNull(nonceValue);
    }
}
