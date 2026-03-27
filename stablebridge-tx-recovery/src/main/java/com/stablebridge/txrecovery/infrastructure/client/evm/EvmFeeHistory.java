package com.stablebridge.txrecovery.infrastructure.client.evm;

import java.util.List;

record EvmFeeHistory(
        String oldestBlock,
        List<String> baseFeePerGas,
        List<Float> gasUsedRatio,
        List<List<String>> reward) {}
