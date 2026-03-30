package com.stablebridge.txrecovery.infrastructure.db.transaction;

import java.util.Set;

import org.springframework.stereotype.Component;

import com.stablebridge.txrecovery.domain.status.port.TransactionCountProvider;
import com.stablebridge.txrecovery.domain.transaction.model.TransactionStatus;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
class TransactionCountProviderAdapter implements TransactionCountProvider {

    private static final Set<String> PENDING_STATUSES = Set.of(
            TransactionStatus.RECEIVED.name(),
            TransactionStatus.BUILDING.name(),
            TransactionStatus.SIGNING.name(),
            TransactionStatus.SUBMITTED.name(),
            TransactionStatus.PENDING.name(),
            TransactionStatus.RECOVERING.name(),
            TransactionStatus.AWAITING_HUMAN.name(),
            TransactionStatus.CANCELLING.name());

    private final TransactionProjectionJpaRepository jpaRepository;

    @Override
    public long countPendingByChain(String chain) {
        return jpaRepository.countByChainAndStatusIn(chain, PENDING_STATUSES);
    }

    @Override
    public long countStuckByChain(String chain) {
        return jpaRepository.countByChainAndStatus(chain, TransactionStatus.STUCK.name());
    }
}
