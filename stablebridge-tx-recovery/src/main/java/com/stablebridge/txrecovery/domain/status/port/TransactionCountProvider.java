package com.stablebridge.txrecovery.domain.status.port;

public interface TransactionCountProvider {

    long countPendingByChain(String chain);

    long countStuckByChain(String chain);
}
