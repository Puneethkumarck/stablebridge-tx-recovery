package com.stablebridge.txrecovery.infrastructure.client.evm;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Objects;

import lombok.Builder;

@Builder(toBuilder = true)
record EvmChainProperties(
        String chain,
        BigDecimal maxFeeCapGwei,
        Duration blockTime) {

    EvmChainProperties {
        Objects.requireNonNull(chain);
        Objects.requireNonNull(maxFeeCapGwei);
        Objects.requireNonNull(blockTime);
    }

    BigDecimal maxFeeCapWei() {
        return maxFeeCapGwei.multiply(BigDecimal.valueOf(1_000_000_000L));
    }
}
