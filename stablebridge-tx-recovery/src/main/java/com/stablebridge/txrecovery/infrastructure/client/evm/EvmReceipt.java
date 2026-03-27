package com.stablebridge.txrecovery.infrastructure.client.evm;

import java.util.List;

record EvmReceipt(
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

    EvmReceipt {
        logs = logs == null ? List.of() : List.copyOf(logs);
    }
}
