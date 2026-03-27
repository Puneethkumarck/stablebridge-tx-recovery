package com.stablebridge.txrecovery.infrastructure.db.nonce;

import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;

import com.stablebridge.txrecovery.domain.address.model.NonceAccountStatus;

interface NonceAccountPoolJpaRepository extends JpaRepository<NonceAccountPoolEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("""
            SELECT n FROM NonceAccountPoolEntity n
            WHERE n.chain = :chain AND n.status = :status
            ORDER BY n.nonceAccount ASC
            LIMIT 1
            """)
    Optional<NonceAccountPoolEntity> findFirstByChainAndStatus(
            String chain, NonceAccountStatus status);

    @Modifying
    @Query("""
            UPDATE NonceAccountPoolEntity n
            SET n.status = :status, n.allocatedToTx = :allocatedToTx
            WHERE n.nonceAccount = :nonceAccount AND n.chain = :chain
            """)
    int updateStatusAndAllocatedToTx(
            String nonceAccount, String chain, NonceAccountStatus status, UUID allocatedToTx);

    @Modifying
    @Query("""
            UPDATE NonceAccountPoolEntity n
            SET n.currentNonceValue = :nonceValue, n.status = :status, n.allocatedToTx = null
            WHERE n.nonceAccount = :nonceAccount AND n.chain = :chain
            """)
    int updateNonceValueAndMarkAvailable(
            String nonceAccount, String chain, String nonceValue, NonceAccountStatus status);

    long countByChainAndStatus(String chain, NonceAccountStatus status);
}
