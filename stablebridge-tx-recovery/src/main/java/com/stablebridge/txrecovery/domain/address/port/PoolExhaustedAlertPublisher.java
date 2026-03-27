package com.stablebridge.txrecovery.domain.address.port;

public interface PoolExhaustedAlertPublisher {

    void publish(String chain, String tier);
}
