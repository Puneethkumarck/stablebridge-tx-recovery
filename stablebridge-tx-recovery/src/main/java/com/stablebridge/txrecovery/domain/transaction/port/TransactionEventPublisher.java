package com.stablebridge.txrecovery.domain.transaction.port;

import com.stablebridge.txrecovery.domain.transaction.event.TransactionLifecycleEvent;

public interface TransactionEventPublisher {

    void publish(TransactionLifecycleEvent event);
}
