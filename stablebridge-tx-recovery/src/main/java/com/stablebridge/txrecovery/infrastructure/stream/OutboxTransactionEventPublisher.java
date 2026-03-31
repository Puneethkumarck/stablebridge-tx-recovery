package com.stablebridge.txrecovery.infrastructure.stream;

import java.util.Optional;

import com.stablebridge.txrecovery.domain.exception.EventSerializationException;
import com.stablebridge.txrecovery.domain.transaction.event.TransactionLifecycleEvent;
import com.stablebridge.txrecovery.domain.transaction.port.TransactionEventPublisher;
import com.stablebridge.txrecovery.infrastructure.db.outbox.OutboxEventPersister;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@RequiredArgsConstructor
@Slf4j
class OutboxTransactionEventPublisher implements TransactionEventPublisher {

    public static final String TOPIC_PREFIX = TransactionLifecycleEvent.TOPIC_PREFIX + ".";

    private final OutboxEventPersister outboxEventPersister;
    private final ObjectMapper objectMapper;

    @Override
    public void publish(TransactionLifecycleEvent event) {
        var topic = resolveTopic(event.chain());
        var key = resolveKey(event);
        var payload = serialize(event);

        outboxEventPersister.persist(event.eventId(), event.intentId(), topic, key, payload);
        log.info("Persisted event {} to outbox for topic {} with key {}", event.eventId(), topic, key);
    }

    static String resolveTopic(String chain) {
        return TOPIC_PREFIX + chain.toLowerCase();
    }

    private String resolveKey(TransactionLifecycleEvent event) {
        return Optional.ofNullable(event.toAddress()).orElseGet(event::intentId);
    }

    private String serialize(TransactionLifecycleEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JacksonException e) {
            throw new EventSerializationException(event.eventId(), e);
        }
    }
}
