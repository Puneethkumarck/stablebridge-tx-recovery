package com.stablebridge.txrecovery.domain.address.model;

import java.util.Objects;

import lombok.Builder;

@Builder(toBuilder = true)
public record NonceAllocation(
        String address,
        String chain,
        long nonce) {

    public NonceAllocation {
        Objects.requireNonNull(address);
        Objects.requireNonNull(chain);
    }
}
