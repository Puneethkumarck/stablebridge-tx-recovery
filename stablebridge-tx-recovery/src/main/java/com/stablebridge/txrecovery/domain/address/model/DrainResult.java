package com.stablebridge.txrecovery.domain.address.model;

import lombok.Builder;

@Builder(toBuilder = true)
public record DrainResult(
        AddressStatus previousStatus,
        PooledAddress address) {}
