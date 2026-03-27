package com.stablebridge.txrecovery.infrastructure.client.evm;

import java.math.BigInteger;
import java.util.Map;
import java.util.Objects;

import com.stablebridge.txrecovery.domain.address.port.OnChainNonceProvider;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class EvmOnChainNonceProvider implements OnChainNonceProvider {

    private final Map<String, EvmRpcClient> rpcClientsByChain;

    @Override
    public BigInteger getTransactionCount(String address, String chain) {
        Objects.requireNonNull(address);
        Objects.requireNonNull(chain);
        var client = rpcClientsByChain.get(chain);
        if (client == null) {
            throw new IllegalArgumentException("No EVM RPC client configured for chain: " + chain);
        }
        return client.getTransactionCount(address, "latest");
    }
}
