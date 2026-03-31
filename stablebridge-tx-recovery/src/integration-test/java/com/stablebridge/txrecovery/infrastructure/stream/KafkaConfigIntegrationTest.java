package com.stablebridge.txrecovery.infrastructure.stream;

import static com.stablebridge.txrecovery.infrastructure.stream.OutboxTransactionEventPublisher.TOPIC_PREFIX;
import static com.stablebridge.txrecovery.testutil.fixtures.TransactionLifecycleEventFixtures.SOME_CHAIN;
import static com.stablebridge.txrecovery.testutil.fixtures.TransactionLifecycleEventFixtures.SOME_EVENT_WITH_FIXED_TIMESTAMP;
import static com.stablebridge.txrecovery.testutil.fixtures.TransactionLifecycleEventFixtures.SOME_FIXED_TIMESTAMP;
import static com.stablebridge.txrecovery.testutil.fixtures.TransactionLifecycleEventFixtures.buildEvent;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;

import com.stablebridge.txrecovery.domain.transaction.event.TransactionLifecycleEvent;
import com.stablebridge.txrecovery.domain.transaction.port.TransactionEventPublisher;
import com.stablebridge.txrecovery.infrastructure.db.outbox.OutboxEventReader;
import com.stablebridge.txrecovery.testutil.IntegrationTestBase;
import com.stablebridge.txrecovery.testutil.KafkaContainerExtension;

@ExtendWith(KafkaContainerExtension.class)
class KafkaConfigIntegrationTest extends IntegrationTestBase {

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private KafkaAdmin kafkaAdmin;

    @Autowired
    private TransactionEventPublisher transactionEventPublisher;

    @Autowired
    private OutboxEventReader outboxEventReader;

    @Autowired
    private OutboxEventRelay outboxEventRelay;

    @Test
    void shouldInjectKafkaTemplate() {
        // when / then
        assertThat(kafkaTemplate).isNotNull();
    }

    @Test
    void shouldInjectKafkaAdmin() {
        // when / then
        assertThat(kafkaAdmin).isNotNull();
    }

    @Test
    void shouldInjectTransactionEventPublisher() {
        // when / then
        assertThat(transactionEventPublisher).isNotNull();
    }

    @Test
    void shouldPersistEventToOutbox() {
        // given
        var event = uniqueEvent();

        // when
        transactionEventPublisher.publish(event);

        // then
        var pending = outboxEventReader.findPending(10);
        assertThat(pending).anyMatch(e -> e.eventId().equals(event.eventId()));
    }

    @Test
    void shouldRelayOutboxEventToKafka() {
        // given
        var topic = TOPIC_PREFIX + SOME_CHAIN;
        var event = uniqueEvent();
        transactionEventPublisher.publish(event);

        // when
        outboxEventRelay.relay();
        kafkaTemplate.flush();

        // then
        try (var consumer = createConsumer()) {
            consumer.subscribe(Collections.singletonList(topic));
            var records = consumer.poll(Duration.ofSeconds(10));
            assertThat(records.count()).isGreaterThanOrEqualTo(1);
            var record = records.iterator().next();
            assertThat(record.key()).isEqualTo(event.toAddress());
            assertThat(record.value()).contains(SOME_CHAIN);
        }
    }

    private TransactionLifecycleEvent uniqueEvent() {
        return buildEvent("0xrecipient").toBuilder()
                .eventId(UUID.randomUUID().toString())
                .build();
    }

    @Nested
    class Serialization {

        @Test
        void shouldSerializeTimestampAsIso8601() {
            // given
            var event = SOME_EVENT_WITH_FIXED_TIMESTAMP;

            // when
            var json = objectMapper.writeValueAsString(event);

            // then
            var tree = objectMapper.readTree(json);
            assertThat(tree.get("timestamp").asText()).isEqualTo(SOME_FIXED_TIMESTAMP.toString());
        }

        @Test
        void shouldSerializeEnumAsString() {
            // given
            var event = SOME_EVENT_WITH_FIXED_TIMESTAMP;

            // when
            var json = objectMapper.writeValueAsString(event);

            // then
            var tree = objectMapper.readTree(json);
            assertThat(tree.get("status").asText()).isEqualTo(event.status().name());
        }
    }

    @SuppressWarnings("unchecked")
    private KafkaConsumer<String, String> createConsumer() {
        var bootstrapServers =
                kafkaTemplate.getProducerFactory().getConfigurationProperties().get("bootstrap.servers");
        return new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ConsumerConfig.GROUP_ID_CONFIG, "test-group-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class));
    }
}
