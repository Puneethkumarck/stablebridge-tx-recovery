package com.stablebridge.txrecovery.domain.model;

import java.time.Instant;
import java.util.Objects;

import lombok.Builder;

@Builder
public record PooledAddress(
        String address,
        String chain,
        ChainFamily chainFamily,
        AddressTier tier,
        AddressStatus status,
        long currentNonce,
        int inFlightCount,
        String signerEndpoint,
        Instant registeredAt) {

    public PooledAddress {
        Objects.requireNonNull(address);
        Objects.requireNonNull(chain);
        Objects.requireNonNull(chainFamily);
        Objects.requireNonNull(tier);
        Objects.requireNonNull(status);
    }
}
