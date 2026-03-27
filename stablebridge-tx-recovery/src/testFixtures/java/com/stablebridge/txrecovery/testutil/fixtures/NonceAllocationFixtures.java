package com.stablebridge.txrecovery.testutil.fixtures;

import com.stablebridge.txrecovery.domain.address.model.NonceAllocation;

public final class NonceAllocationFixtures {

    public static final String SOME_ADDRESS = "0x742d35Cc6634C0532925a3b844Bc9e7595f2bD28";
    public static final String SOME_CHAIN = "ethereum_mainnet";
    public static final String SOME_HASH_KEY =
            "str:nonce:ethereum_mainnet:0x742d35Cc6634C0532925a3b844Bc9e7595f2bD28";
    public static final String SOME_INFLIGHT_KEY =
            "str:nonce:inflight:ethereum_mainnet:0x742d35Cc6634C0532925a3b844Bc9e7595f2bD28";

    private NonceAllocationFixtures() {}

    public static NonceAllocation someAllocation(long nonce) {
        return NonceAllocation.builder()
                .address(SOME_ADDRESS)
                .chain(SOME_CHAIN)
                .nonce(nonce)
                .build();
    }
}
