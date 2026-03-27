package com.stablebridge.txrecovery.infrastructure.client.evm;

import java.util.List;
import java.util.Optional;

import lombok.Builder;

@Builder(toBuilder = true)
public record EvmLog(
        String address,
        List<String> topics,
        String data,
        String blockNumber,
        String blockHash,
        String transactionHash,
        String transactionIndex,
        String logIndex,
        boolean removed) {

    public EvmLog {
        topics = Optional.ofNullable(topics).map(List::copyOf).orElse(List.of());
    }
}
