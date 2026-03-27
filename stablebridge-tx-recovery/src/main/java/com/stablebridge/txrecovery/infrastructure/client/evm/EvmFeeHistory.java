package com.stablebridge.txrecovery.infrastructure.client.evm;

import java.util.List;

record EvmFeeHistory(
        String oldestBlock,
        List<String> baseFeePerGas,
        List<Float> gasUsedRatio,
        List<List<String>> reward) {

    EvmFeeHistory {
        baseFeePerGas = baseFeePerGas == null ? List.of() : List.copyOf(baseFeePerGas);
        gasUsedRatio = gasUsedRatio == null ? List.of() : List.copyOf(gasUsedRatio);
        reward = reward == null ? List.of() : reward.stream()
                .map(inner -> inner == null ? List.<String>of() : List.copyOf(inner))
                .toList();
    }
}
