package com.stablebridge.txrecovery.infrastructure.db.outbox;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "outbox_event")
@Getter
@Setter
class OutboxEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "event_id", nullable = false)
    private String eventId;

    @Column(name = "intent_id", nullable = false)
    private String intentId;

    @Column(nullable = false)
    private String topic;

    @Column(name = "partition_key", nullable = false)
    private String partitionKey;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private OutboxEventStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;
}
