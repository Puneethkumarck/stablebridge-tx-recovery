package com.stablebridge.txrecovery.infrastructure.db.outbox;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface OutboxEventJpaRepository extends JpaRepository<OutboxEventEntity, UUID> {

    @Query("""
            SELECT e FROM OutboxEventEntity e
            WHERE e.status = 'PENDING'
            ORDER BY e.createdAt ASC
            LIMIT :batchSize
            """)
    List<OutboxEventEntity> findPendingOrderByCreatedAt(int batchSize);

    Optional<OutboxEventEntity> findByEventId(String eventId);

    @Modifying
    @Query("""
            UPDATE OutboxEventEntity e
            SET e.status = :status, e.publishedAt = :publishedAt
            WHERE e.eventId = :eventId
            """)
    int updateStatusAndPublishedAt(String eventId, OutboxEventStatus status, Instant publishedAt);
}
