package com.stablebridge.txrecovery.application.config;

import static com.stablebridge.txrecovery.domain.transaction.model.TransactionStatus.SUBMITTED;
import static com.stablebridge.txrecovery.infrastructure.stream.KafkaTransactionEventPublisher.TOPIC_PREFIX;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;

import com.stablebridge.txrecovery.domain.transaction.event.TransactionLifecycleEvent;
import com.stablebridge.txrecovery.domain.transaction.port.TransactionEventPublisher;
import com.stablebridge.txrecovery.testutil.KafkaTest;
import com.stablebridge.txrecovery.testutil.PostgresContainerExtension;

import tools.jackson.databind.ObjectMapper;

@KafkaTest
@ExtendWith(PostgresContainerExtension.class)
class KafkaConfigIntegrationTest {

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private KafkaAdmin kafkaAdmin;

    @Autowired
    private TransactionEventPublisher transactionEventPublisher;

    @Autowired
    private ObjectMapper objectMapper;

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
    void shouldPublishEventToPerChainTopic() {
        // given
        var topic = TOPIC_PREFIX + "ethereum_mainnet";
        var event = TransactionLifecycleEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .intentId(UUID.randomUUID().toString())
                .toAddress("0xrecipient")
                .chain("ethereum_mainnet")
                .status(SUBMITTED)
                .timestamp(Instant.now())
                .build();

        // when
        transactionEventPublisher.publish(event);
        kafkaTemplate.flush();

        // then
        try (var consumer = createConsumer()) {
            consumer.subscribe(Collections.singletonList(topic));
            var records = consumer.poll(Duration.ofSeconds(10));
            assertThat(records.count()).isGreaterThanOrEqualTo(1);
            var record = records.iterator().next();
            assertThat(record.key()).isEqualTo("0xrecipient");
            assertThat(record.value()).contains("ethereum_mainnet");
        }
    }

    @Test
    void shouldSerializeTimestampAsIso8601() {
        // given
        var event = TransactionLifecycleEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .intentId(UUID.randomUUID().toString())
                .toAddress("0xrecipient")
                .chain("ethereum_mainnet")
                .status(SUBMITTED)
                .timestamp(Instant.parse("2026-03-27T12:00:00Z"))
                .build();

        // when
        var json = objectMapper.writeValueAsString(event);

        // then
        assertThat(json).contains("2026-03-27T12:00:00Z");
        assertThat(json).doesNotContain("1.74");
    }

    @Test
    void shouldSerializeEnumAsString() {
        // given
        var event = TransactionLifecycleEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .intentId(UUID.randomUUID().toString())
                .toAddress("0xrecipient")
                .chain("ethereum_mainnet")
                .status(SUBMITTED)
                .timestamp(Instant.now())
                .build();

        // when
        var json = objectMapper.writeValueAsString(event);

        // then
        assertThat(json).contains("\"SUBMITTED\"");
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
