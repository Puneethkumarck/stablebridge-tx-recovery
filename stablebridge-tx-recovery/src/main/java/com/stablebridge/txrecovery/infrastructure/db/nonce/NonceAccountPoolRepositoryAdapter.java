package com.stablebridge.txrecovery.infrastructure.db.nonce;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.stablebridge.txrecovery.domain.address.model.NonceAccountStatus;
import com.stablebridge.txrecovery.domain.address.model.SolanaNonceAccount;
import com.stablebridge.txrecovery.domain.address.port.NonceAccountPoolRepository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
class NonceAccountPoolRepositoryAdapter implements NonceAccountPoolRepository {

    private final NonceAccountPoolJpaRepository jpaRepository;
    private final NonceAccountPoolEntityMapper mapper;

    @Override
    @Transactional(readOnly = true)
    public Optional<SolanaNonceAccount> findAvailableByChain(String chain) {
        return jpaRepository.findFirstByChainAndStatus(chain, NonceAccountStatus.AVAILABLE)
                .map(mapper::toDomain);
    }

    @Override
    @Transactional
    public void markInUse(String nonceAccountAddress, String chain, String allocatedToTx) {
        var updated = jpaRepository.updateStatusAndAllocatedToTx(
                nonceAccountAddress, chain, NonceAccountStatus.IN_USE, UUID.fromString(allocatedToTx));
        if (updated == 0) {
            throw new IllegalStateException(
                    "Nonce account not found: address=%s chain=%s".formatted(nonceAccountAddress, chain));
        }
    }

    @Override
    @Transactional
    public void markAvailable(String nonceAccountAddress, String chain) {
        var updated = jpaRepository.updateStatusAndAllocatedToTx(
                nonceAccountAddress, chain, NonceAccountStatus.AVAILABLE, null);
        if (updated == 0) {
            throw new IllegalStateException(
                    "Nonce account not found: address=%s chain=%s".formatted(nonceAccountAddress, chain));
        }
    }

    @Override
    @Transactional
    public void updateNonceValue(String nonceAccountAddress, String chain, String newNonceValue) {
        var updated = jpaRepository.updateNonceValue(nonceAccountAddress, chain, newNonceValue);
        if (updated == 0) {
            throw new IllegalStateException(
                    "Nonce account not found: address=%s chain=%s".formatted(nonceAccountAddress, chain));
        }
    }

    @Override
    @Transactional
    public void consumeAndRelease(String nonceAccountAddress, String chain, String newNonceValue) {
        var updated = jpaRepository.updateNonceValueAndMarkAvailable(
                nonceAccountAddress, chain, newNonceValue, NonceAccountStatus.AVAILABLE);
        if (updated == 0) {
            throw new IllegalStateException(
                    "Nonce account not found: address=%s chain=%s".formatted(nonceAccountAddress, chain));
        }
    }

    @Override
    @Transactional(readOnly = true)
    public long countAvailableByChain(String chain) {
        return jpaRepository.countByChainAndStatus(chain, NonceAccountStatus.AVAILABLE);
    }
}
