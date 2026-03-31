package com.stablebridge.txrecovery.infrastructure.db.outbox;

import java.time.Clock;
import java.time.Instant;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class OutboxEventPersister {

    private final OutboxEventJpaRepository outboxRepository;
    private final Clock clock;

    @Transactional
    public void persist(String eventId, String intentId, String topic, String partitionKey, String payload) {
        var entity = new OutboxEventEntity();
        entity.setEventId(eventId);
        entity.setIntentId(intentId);
        entity.setTopic(topic);
        entity.setPartitionKey(partitionKey);
        entity.setPayload(payload);
        entity.setStatus(OutboxEventStatus.PENDING);
        entity.setCreatedAt(Instant.now(clock));
        entity.setRetryCount(0);
        outboxRepository.save(entity);
    }
}
