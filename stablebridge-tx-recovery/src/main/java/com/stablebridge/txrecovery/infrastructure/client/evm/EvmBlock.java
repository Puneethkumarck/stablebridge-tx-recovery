package com.stablebridge.txrecovery.infrastructure.client.evm;

import java.util.List;
import java.util.Optional;

import lombok.Builder;

@Builder(toBuilder = true)
record EvmBlock(
        String number,
        String hash,
        String parentHash,
        String timestamp,
        String gasLimit,
        String gasUsed,
        String baseFeePerGas,
        String miner,
        List<Object> transactions) {

    EvmBlock {
        transactions = Optional.ofNullable(transactions).map(List::copyOf).orElse(List.of());
    }
}
