package com.stablebridge.txrecovery.domain.model;

import java.util.Objects;

import lombok.Builder;

@Builder
public record NonceAllocation(
        String address,
        String chain,
        long nonce) {

    public NonceAllocation {
        Objects.requireNonNull(address);
        Objects.requireNonNull(chain);
    }
}
