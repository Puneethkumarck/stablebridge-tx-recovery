package com.stablebridge.txrecovery.infrastructure.db.address;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.stablebridge.txrecovery.domain.address.model.AddressStatus;

interface AddressPoolJpaRepository extends JpaRepository<AddressPoolEntity, UUID> {

    @Query("""
            SELECT a FROM AddressPoolEntity a
            WHERE a.chain = :chain
              AND a.tier = :tier
              AND a.status = :status
              AND a.inFlightCount < :maxInFlight
            ORDER BY a.inFlightCount ASC, a.lastUsedAt ASC NULLS FIRST
            LIMIT 1
            """)
    Optional<AddressPoolEntity> findBestCandidate(
            String chain, String tier, AddressStatus status, int maxInFlight);

    @Modifying
    @Query("""
            UPDATE AddressPoolEntity a
            SET a.inFlightCount = a.inFlightCount + 1, a.lastUsedAt = :now
            WHERE a.address = :address AND a.chain = :chain
            """)
    int incrementInFlightCount(String address, String chain, Instant now);

    @Modifying
    @Query("""
            UPDATE AddressPoolEntity a
            SET a.inFlightCount = a.inFlightCount - 1
            WHERE a.address = :address AND a.chain = :chain AND a.inFlightCount > 0
            """)
    int decrementInFlightCount(String address, String chain);

    Optional<AddressPoolEntity> findByAddressAndChain(String address, String chain);
}
