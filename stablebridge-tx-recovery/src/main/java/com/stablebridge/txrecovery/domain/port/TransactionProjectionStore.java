package com.stablebridge.txrecovery.domain.port;

import java.util.List;
import java.util.Optional;

import com.stablebridge.txrecovery.domain.model.TransactionFilters;
import com.stablebridge.txrecovery.domain.model.TransactionProjection;

public interface TransactionProjectionStore {

    void save(TransactionProjection projection);

    Optional<TransactionProjection> findById(String id);

    List<TransactionProjection> findByFilters(TransactionFilters filters);
}
