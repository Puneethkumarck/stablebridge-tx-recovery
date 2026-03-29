package com.stablebridge.txrecovery.domain.transaction.port;

import java.util.Optional;

import com.stablebridge.txrecovery.domain.exception.DuplicateIntentException;
import com.stablebridge.txrecovery.domain.transaction.model.TransactionIntent;

public interface TransactionIntentStore {

    void save(TransactionIntent intent) throws DuplicateIntentException;

    Optional<TransactionIntent> findByIntentId(String intentId);
}
