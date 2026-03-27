package com.stablebridge.txrecovery.infrastructure.db.nonce;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface NonceAccountPoolJpaRepository extends JpaRepository<NonceAccountPoolEntity, UUID> {

    @Query("""
            SELECT n FROM NonceAccountPoolEntity n
            WHERE n.chain = :chain AND n.status = :status
            ORDER BY n.nonceAccount ASC
            LIMIT 1
            """)
    Optional<NonceAccountPoolEntity> findFirstByChainAndStatus(String chain, String status);

    @Modifying
    @Query("""
            UPDATE NonceAccountPoolEntity n
            SET n.status = :status, n.allocatedToTx = :allocatedToTx
            WHERE n.nonceAccount = :nonceAccount AND n.chain = :chain
            """)
    int updateStatusAndAllocatedToTx(String nonceAccount, String chain, String status, UUID allocatedToTx);

    @Modifying
    @Query("""
            UPDATE NonceAccountPoolEntity n
            SET n.currentNonceValue = :nonceValue
            WHERE n.nonceAccount = :nonceAccount AND n.chain = :chain
            """)
    int updateNonceValue(String nonceAccount, String chain, String nonceValue);

    long countByChainAndStatus(String chain, String status);
}
