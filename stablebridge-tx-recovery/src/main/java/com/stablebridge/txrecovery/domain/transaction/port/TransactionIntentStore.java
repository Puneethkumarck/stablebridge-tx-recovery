package com.stablebridge.txrecovery.domain.transaction.port;

import java.util.Optional;

import com.stablebridge.txrecovery.domain.transaction.model.TransactionIntent;

public interface TransactionIntentStore {

    void save(TransactionIntent intent);

    Optional<TransactionIntent> findByIntentId(String intentId);
}
