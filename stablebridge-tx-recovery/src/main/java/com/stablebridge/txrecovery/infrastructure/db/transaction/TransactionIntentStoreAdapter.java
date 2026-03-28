package com.stablebridge.txrecovery.infrastructure.db.transaction;

import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.stablebridge.txrecovery.domain.transaction.model.TransactionIntent;
import com.stablebridge.txrecovery.domain.transaction.port.TransactionIntentStore;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
class TransactionIntentStoreAdapter implements TransactionIntentStore {

    private final TransactionIntentJpaRepository jpaRepository;
    private final TransactionIntentEntityMapper mapper;

    @Override
    public void save(TransactionIntent intent) {
        var entity = mapper.toEntity(intent);
        jpaRepository.save(entity);
    }

    @Override
    public Optional<TransactionIntent> findByIntentId(String intentId) {
        return jpaRepository.findByIntentId(intentId)
                .map(mapper::toDomain);
    }
}
