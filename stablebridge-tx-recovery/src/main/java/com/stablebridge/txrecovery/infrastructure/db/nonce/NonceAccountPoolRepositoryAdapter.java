package com.stablebridge.txrecovery.infrastructure.db.nonce;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.stablebridge.txrecovery.domain.address.model.SolanaNonceAccount;
import com.stablebridge.txrecovery.domain.address.port.NonceAccountPoolRepository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
class NonceAccountPoolRepositoryAdapter implements NonceAccountPoolRepository {

    private static final String AVAILABLE = "AVAILABLE";
    private static final String IN_USE = "IN_USE";

    private final NonceAccountPoolJpaRepository jpaRepository;
    private final NonceAccountPoolEntityMapper mapper;

    @Override
    @Transactional(readOnly = true)
    public Optional<SolanaNonceAccount> findAvailableByChain(String chain) {
        return jpaRepository.findFirstByChainAndStatus(chain, AVAILABLE)
                .map(mapper::toDomain);
    }

    @Override
    @Transactional
    public void markInUse(String nonceAccountAddress, String chain, String allocatedToTx) {
        jpaRepository.updateStatusAndAllocatedToTx(
                nonceAccountAddress, chain, IN_USE, UUID.fromString(allocatedToTx));
    }

    @Override
    @Transactional
    public void markAvailable(String nonceAccountAddress, String chain) {
        jpaRepository.updateStatusAndAllocatedToTx(nonceAccountAddress, chain, AVAILABLE, null);
    }

    @Override
    @Transactional
    public void updateNonceValue(String nonceAccountAddress, String chain, String newNonceValue) {
        jpaRepository.updateNonceValue(nonceAccountAddress, chain, newNonceValue);
    }

    @Override
    @Transactional(readOnly = true)
    public long countAvailableByChain(String chain) {
        return jpaRepository.countByChainAndStatus(chain, AVAILABLE);
    }
}
