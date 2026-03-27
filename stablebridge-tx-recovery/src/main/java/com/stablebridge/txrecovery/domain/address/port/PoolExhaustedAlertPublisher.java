package com.stablebridge.txrecovery.domain.address.port;

import com.stablebridge.txrecovery.domain.address.model.AddressTier;

public interface PoolExhaustedAlertPublisher {

    void publish(String chain, AddressTier tier);
}
