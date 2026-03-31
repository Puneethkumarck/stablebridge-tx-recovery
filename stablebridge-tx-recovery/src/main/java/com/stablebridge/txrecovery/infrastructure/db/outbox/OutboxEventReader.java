package com.stablebridge.txrecovery.infrastructure.db.outbox;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class OutboxEventReader {

    private final OutboxEventJpaRepository outboxRepository;

    @Transactional(readOnly = true)
    public List<PendingOutboxEvent> findPending(int batchSize) {
        return outboxRepository.findPendingOrderByCreatedAt(batchSize).stream()
                .map(e -> new PendingOutboxEvent(
                        e.getId().toString(),
                        e.getEventId(),
                        e.getTopic(),
                        e.getPartitionKey(),
                        e.getPayload()))
                .toList();
    }

    @Transactional
    public void markPublished(String eventId) {
        outboxRepository.updateStatusAndPublishedAt(eventId, OutboxEventStatus.PUBLISHED, Instant.now());
    }

    @Transactional
    public void incrementRetryOrFail(String eventId, int maxRetries) {
        var entity = outboxRepository.findByEventId(eventId);
        entity.ifPresent(e -> {
            e.setRetryCount(e.getRetryCount() + 1);
            if (e.getRetryCount() >= maxRetries) {
                e.setStatus(OutboxEventStatus.FAILED);
            }
            outboxRepository.save(e);
        });
    }

    public record PendingOutboxEvent(
            String id,
            String eventId,
            String topic,
            String partitionKey,
            String payload) {}
}
