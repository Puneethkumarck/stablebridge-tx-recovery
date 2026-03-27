package com.stablebridge.txrecovery.infrastructure.client.evm;

import java.util.List;

record EvmBlock(
        String number,
        String hash,
        String parentHash,
        String timestamp,
        String gasLimit,
        String gasUsed,
        String baseFeePerGas,
        String miner,
        List<Object> transactions) {}
