package com.stablebridge.txrecovery.infrastructure.db.transaction;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

interface TransactionProjectionJpaRepository
        extends JpaRepository<TransactionProjectionEntity, UUID>,
                JpaSpecificationExecutor<TransactionProjectionEntity> {

    Optional<TransactionProjectionEntity> findByIntentId(UUID intentId);

    long countByChainAndStatus(String chain, String status);

    long countByChainAndStatusIn(String chain, Set<String> statuses);

    @Query(
            value = """
            SELECT COALESCE(AVG(EXTRACT(EPOCH FROM (confirmed_at - created_at)) * 1000), 0)
            FROM transaction_projection
            WHERE chain = :chain AND status = 'CONFIRMED' AND confirmed_at IS NOT NULL
            """,
            nativeQuery = true)
    long averageConfirmationTimeMsByChain(String chain);
}
