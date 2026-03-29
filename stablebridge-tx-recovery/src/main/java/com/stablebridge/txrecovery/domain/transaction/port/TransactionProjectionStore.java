package com.stablebridge.txrecovery.domain.transaction.port;

import java.util.Optional;

import com.stablebridge.txrecovery.domain.transaction.model.PagedResult;
import com.stablebridge.txrecovery.domain.transaction.model.TransactionFilters;
import com.stablebridge.txrecovery.domain.transaction.model.TransactionProjection;

public interface TransactionProjectionStore {

    void save(TransactionProjection projection);

    Optional<TransactionProjection> findById(String id);

    Optional<TransactionProjection> findByIntentId(String intentId);

    PagedResult<TransactionProjection> findByFilters(TransactionFilters filters, int page, int size);
}
