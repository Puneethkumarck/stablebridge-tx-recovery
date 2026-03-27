package com.stablebridge.txrecovery.infrastructure.client.solana;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

import lombok.Builder;

@Builder(toBuilder = true)
record SolanaChainProperties(
        String chain,
        long maxPriorityFeeMicroLamports,
        Duration blockTime,
        List<String> programAddresses) {

    SolanaChainProperties {
        Objects.requireNonNull(chain);
        Objects.requireNonNull(blockTime);
        Objects.requireNonNull(programAddresses);
        if (maxPriorityFeeMicroLamports <= 0) {
            throw new IllegalArgumentException("maxPriorityFeeMicroLamports must be positive");
        }
        programAddresses = List.copyOf(programAddresses);
    }
}
