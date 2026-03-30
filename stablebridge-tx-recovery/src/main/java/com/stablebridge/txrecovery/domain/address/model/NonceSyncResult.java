package com.stablebridge.txrecovery.domain.address.model;

import java.util.Objects;

import lombok.Builder;

@Builder(toBuilder = true)
public record NonceSyncResult(
        long previousNonce,
        long currentNonce,
        PooledAddress address) {

    public NonceSyncResult {
        Objects.requireNonNull(address);
    }
}
