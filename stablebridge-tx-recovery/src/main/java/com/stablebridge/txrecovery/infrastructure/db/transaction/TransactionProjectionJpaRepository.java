package com.stablebridge.txrecovery.infrastructure.db.transaction;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

interface TransactionProjectionJpaRepository
        extends JpaRepository<TransactionProjectionEntity, UUID>,
                JpaSpecificationExecutor<TransactionProjectionEntity> {

    Optional<TransactionProjectionEntity> findByIntentId(UUID intentId);
}
