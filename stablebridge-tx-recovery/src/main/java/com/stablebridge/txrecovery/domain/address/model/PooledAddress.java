package com.stablebridge.txrecovery.domain.address.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.stablebridge.txrecovery.domain.common.model.StateProvider;

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
        Instant lastUsedAt) implements StateProvider<AddressStatus> {

    public PooledAddress {
        Objects.requireNonNull(address);
        Objects.requireNonNull(chain);
        Objects.requireNonNull(chainFamily);
        Objects.requireNonNull(tier);
        Objects.requireNonNull(status);
    }

    @Override
    public AddressStatus state() {
        return status;
    }
}
