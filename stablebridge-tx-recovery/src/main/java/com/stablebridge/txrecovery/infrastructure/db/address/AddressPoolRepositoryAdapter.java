package com.stablebridge.txrecovery.infrastructure.db.address;

import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.stablebridge.txrecovery.domain.address.model.AddressStatus;
import com.stablebridge.txrecovery.domain.address.model.AddressTier;
import com.stablebridge.txrecovery.domain.address.model.PooledAddress;
import com.stablebridge.txrecovery.domain.address.port.AddressPoolRepository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
class AddressPoolRepositoryAdapter implements AddressPoolRepository {

    private final AddressPoolJpaRepository jpaRepository;
    private final AddressPoolEntityMapper mapper;

    @Override
    @Transactional
    public Optional<PooledAddress> findBestCandidate(
            String chain, AddressTier tier, AddressStatus status, int maxInFlight) {
        return jpaRepository.findBestCandidate(chain, tier, status, maxInFlight)
                .map(mapper::toDomain);
    }

    @Override
    @Transactional
    public void incrementInFlightCount(String address, String chain) {
        jpaRepository.incrementInFlightCount(address, chain, Instant.now());
    }

    @Override
    @Transactional
    public void decrementInFlightCount(String address, String chain) {
        jpaRepository.decrementInFlightCount(address, chain);
    }

    @Override
    @Transactional
    public PooledAddress save(PooledAddress pooledAddress) {
        var entity = mapper.toEntity(pooledAddress);
        var saved = jpaRepository.save(entity);
        return mapper.toDomain(saved);
    }
}
