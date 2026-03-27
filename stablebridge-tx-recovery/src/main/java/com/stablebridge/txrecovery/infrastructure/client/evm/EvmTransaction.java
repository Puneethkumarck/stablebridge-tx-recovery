package com.stablebridge.txrecovery.infrastructure.client.evm;

import lombok.Builder;

@Builder(toBuilder = true)
record EvmTransaction(
        String hash,
        String nonce,
        String blockHash,
        String blockNumber,
        String transactionIndex,
        String from,
        String to,
        String value,
        String gas,
        String gasPrice,
        String maxFeePerGas,
        String maxPriorityFeePerGas,
        String input,
        String type) {}
