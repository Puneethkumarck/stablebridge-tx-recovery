package com.stablebridge.txrecovery.domain.address.model;

import lombok.Builder;

@Builder(toBuilder = true)
public record NonceSyncResult(
        long previousNonce,
        long currentNonce,
        PooledAddress address) {}
