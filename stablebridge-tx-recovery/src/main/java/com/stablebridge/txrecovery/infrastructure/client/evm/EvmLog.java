package com.stablebridge.txrecovery.infrastructure.client.evm;

import java.util.List;

record EvmLog(
        String address,
        List<String> topics,
        String data,
        String blockNumber,
        String blockHash,
        String transactionHash,
        String transactionIndex,
        String logIndex,
        boolean removed) {}
