package com.stablebridge.txrecovery.infrastructure.status;

import java.util.Map;

import org.springframework.stereotype.Component;

import com.stablebridge.txrecovery.domain.status.port.RpcHealthProvider;
import com.stablebridge.txrecovery.infrastructure.client.evm.EvmRpcClient;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class RpcHealthProviderAdapter implements RpcHealthProvider {

    private final Map<String, EvmRpcClient> evmRpcClients;

    @Override
    public long measureRpcLatencyMs(String chain) {
        var client = evmRpcClients.get(chain);
        if (client == null) {
            return -1;
        }
        try {
            var start = System.nanoTime();
            client.getBlockByNumber("latest", false);
            return (System.nanoTime() - start) / 1_000_000;
        } catch (Exception e) {
            log.warn("RPC latency measurement failed for chain {}: {}", chain, e.getMessage());
            return -1;
        }
    }

    @Override
    public long getLastBlockNumber(String chain) {
        var client = evmRpcClients.get(chain);
        if (client == null) {
            return -1;
        }
        try {
            var block = client.getBlockByNumber("latest", false);
            return Long.decode(block.number());
        } catch (Exception e) {
            log.warn("Failed to fetch last block for chain {}: {}", chain, e.getMessage());
            return -1;
        }
    }
}
