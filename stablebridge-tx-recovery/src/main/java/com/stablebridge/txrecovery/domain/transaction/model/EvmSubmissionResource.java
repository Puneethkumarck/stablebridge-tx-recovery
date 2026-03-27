package com.stablebridge.txrecovery.domain.transaction.model;

import java.util.Objects;

import com.stablebridge.txrecovery.domain.address.model.AddressTier;

import lombok.Builder;

@Builder(toBuilder = true)
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
