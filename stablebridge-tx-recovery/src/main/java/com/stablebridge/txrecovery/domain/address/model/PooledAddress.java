package com.stablebridge.txrecovery.domain.address.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import lombok.Builder;

@Builder(toBuilder = true)
public record PooledAddress(
        UUID id,
        String address,
        String chain,
        ChainFamily chainFamily,
        AddressTier tier,
        AddressStatus status,
        long currentNonce,
        int inFlightCount,
        String signerEndpoint,
        Instant registeredAt,
        Instant retiredAt,
        Instant lastUsedAt) {

    public PooledAddress {
        Objects.requireNonNull(address);
        Objects.requireNonNull(chain);
        Objects.requireNonNull(chainFamily);
        Objects.requireNonNull(tier);
        Objects.requireNonNull(status);
    }
}
