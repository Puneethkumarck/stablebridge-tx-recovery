package com.stablebridge.txrecovery.domain.port;

import com.stablebridge.txrecovery.domain.event.TransactionLifecycleEvent;

public interface TransactionEventPublisher {

    void publish(TransactionLifecycleEvent event);
}
