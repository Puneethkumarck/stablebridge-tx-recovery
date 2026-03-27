package com.stablebridge.txrecovery.infrastructure.db.nonce;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.stablebridge.txrecovery.domain.address.model.NonceAccountStatus;
import com.stablebridge.txrecovery.domain.address.model.SolanaNonceAccount;
import com.stablebridge.txrecovery.domain.address.port.NonceAccountPoolRepository;
import com.stablebridge.txrecovery.domain.exception.NonceAccountNotFoundException;
import com.stablebridge.txrecovery.domain.exception.NonceConcurrencyException;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
class NonceAccountPoolRepositoryAdapter implements NonceAccountPoolRepository {

    private final NonceAccountPoolJpaRepository jpaRepository;
    private final NonceAccountPoolEntityMapper mapper;

    @Override
    @Transactional
    public Optional<SolanaNonceAccount> findAvailableAndMarkInUse(String chain, String allocatedToTx) {
        var entity = jpaRepository.findFirstByChainAndStatus(chain, NonceAccountStatus.AVAILABLE);
        entity.ifPresent(e -> {
            var updated = jpaRepository.updateStatusAndAllocatedToTx(
                    e.getNonceAccount(), chain, NonceAccountStatus.IN_USE, UUID.fromString(allocatedToTx));
            if (updated == 0) {
                throw new NonceConcurrencyException(e.getNonceAccount(), chain);
            }
            e.setStatus(NonceAccountStatus.IN_USE);
            e.setAllocatedToTx(UUID.fromString(allocatedToTx));
        });
        return entity.map(mapper::toDomain);
    }

    @Override
    @Transactional
    public void markAvailable(String nonceAccountAddress, String chain) {
        var updated = jpaRepository.updateStatusAndAllocatedToTx(
                nonceAccountAddress, chain, NonceAccountStatus.AVAILABLE, null);
        if (updated == 0) {
            throw new NonceAccountNotFoundException(nonceAccountAddress, chain);
        }
    }

    @Override
    @Transactional
    public void consumeAndRelease(String nonceAccountAddress, String chain, String newNonceValue) {
        var updated = jpaRepository.updateNonceValueAndMarkAvailable(
                nonceAccountAddress, chain, newNonceValue, NonceAccountStatus.AVAILABLE);
        if (updated == 0) {
            throw new NonceAccountNotFoundException(nonceAccountAddress, chain);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public long countAvailableByChain(String chain) {
        return jpaRepository.countByChainAndStatus(chain, NonceAccountStatus.AVAILABLE);
    }
}
