package com.stablebridge.txrecovery.domain.model;

import java.util.Objects;

import lombok.Builder;

@Builder
public record EvmSubmissionResource(
        String chain,
        String fromAddress,
        long nonce,
        AddressTier tier) implements SubmissionResource {

    public EvmSubmissionResource {
        Objects.requireNonNull(chain);
        Objects.requireNonNull(fromAddress);
        Objects.requireNonNull(tier);
    }
}
