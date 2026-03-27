package com.stablebridge.txrecovery.infrastructure.client.evm;

import java.util.List;
import java.util.Optional;

record EvmFeeHistory(
        String oldestBlock,
        List<String> baseFeePerGas,
        List<Float> gasUsedRatio,
        List<List<String>> reward) {

    EvmFeeHistory {
        baseFeePerGas = Optional.ofNullable(baseFeePerGas).map(List::copyOf).orElse(List.of());
        gasUsedRatio = Optional.ofNullable(gasUsedRatio).map(List::copyOf).orElse(List.of());
        reward = Optional.ofNullable(reward)
                .map(r -> r.stream()
                        .map(inner -> Optional.ofNullable(inner)
                                .map(List::copyOf)
                                .orElse(List.of()))
                        .toList())
                .orElse(List.of());
    }
}
