package com.stablebridge.txrecovery.infrastructure.client.evm;

import java.util.List;
import java.util.Optional;

import lombok.Builder;

@Builder(toBuilder = true)
public record EvmReceipt(
        String transactionHash,
        String transactionIndex,
        String blockHash,
        String blockNumber,
        String from,
        String to,
        String cumulativeGasUsed,
        String gasUsed,
        String effectiveGasPrice,
        String status,
        List<EvmLog> logs,
        String contractAddress,
        String type) {

    public EvmReceipt {
        logs = Optional.ofNullable(logs).map(List::copyOf).orElse(List.of());
    }
}
