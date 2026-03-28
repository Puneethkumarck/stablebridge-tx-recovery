package com.stablebridge.txrecovery.infrastructure.stream;

import java.util.Optional;

import org.springframework.kafka.core.KafkaTemplate;

import com.stablebridge.txrecovery.domain.exception.EventSerializationException;
import com.stablebridge.txrecovery.domain.transaction.event.TransactionLifecycleEvent;
import com.stablebridge.txrecovery.domain.transaction.port.TransactionEventPublisher;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@RequiredArgsConstructor
@Slf4j
public class KafkaTransactionEventPublisher implements TransactionEventPublisher {

    public static final String TOPIC_PREFIX = TransactionLifecycleEvent.TOPIC_PREFIX + ".";
    public static final String DLQ_PREFIX = "str.tx.dlq.";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void publish(TransactionLifecycleEvent event) {
        var topic = resolveTopic(event.chain());
        var key = resolveKey(event);
        var payload = serialize(event);

        log.info("Publishing event {} to topic {} with key {}", event.eventId(), topic, key);

        var future = kafkaTemplate.send(topic, key, payload);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to publish event {} to topic {}", event.eventId(), topic, ex);
                publishToDlq(event, key, payload);
            } else {
                log.info(
                        "Published event {} to topic {} partition {} offset {}",
                        event.eventId(),
                        topic,
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            }
        });
    }

    private String resolveKey(TransactionLifecycleEvent event) {
        return Optional.ofNullable(event.toAddress()).orElseGet(event::intentId);
    }

    private String resolveTopic(String chain) {
        return TOPIC_PREFIX + chain.toLowerCase();
    }

    private String serialize(TransactionLifecycleEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JacksonException e) {
            throw new EventSerializationException(event.eventId(), e);
        }
    }

    private void publishToDlq(TransactionLifecycleEvent event, String key, String payload) {
        var dlqTopic = DLQ_PREFIX + event.chain().toLowerCase();
        try {
            kafkaTemplate.send(dlqTopic, key, payload);
            log.info("Published failed event {} to DLQ topic {}", event.eventId(), dlqTopic);
        } catch (Exception dlqEx) {
            log.error("Failed to publish event {} to DLQ topic {}", event.eventId(), dlqTopic, dlqEx);
        }
    }
}
