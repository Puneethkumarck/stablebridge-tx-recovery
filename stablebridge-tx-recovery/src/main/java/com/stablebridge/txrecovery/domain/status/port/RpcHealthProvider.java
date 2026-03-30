package com.stablebridge.txrecovery.domain.status.port;

public interface RpcHealthProvider {

    long measureRpcLatencyMs(String chain);

    long getLastBlockNumber(String chain);
}
