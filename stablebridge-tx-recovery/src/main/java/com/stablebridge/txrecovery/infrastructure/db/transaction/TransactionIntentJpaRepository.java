package com.stablebridge.txrecovery.infrastructure.db.transaction;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface TransactionIntentJpaRepository extends JpaRepository<TransactionIntentEntity, UUID> {

    Optional<TransactionIntentEntity> findByIntentId(String intentId);
}
