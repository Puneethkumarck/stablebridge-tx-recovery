package com.stablebridge.txrecovery.infrastructure.stream;

import static com.stablebridge.txrecovery.infrastructure.stream.KafkaTransactionEventPublisher.TOPIC_PREFIX;
import static com.stablebridge.txrecovery.testutil.fixtures.TransactionLifecycleEventFixtures.SOME_CHAIN;
import static com.stablebridge.txrecovery.testutil.fixtures.TransactionLifecycleEventFixtures.SOME_EVENT;
import static com.stablebridge.txrecovery.testutil.fixtures.TransactionLifecycleEventFixtures.SOME_EVENT_WITH_FIXED_TIMESTAMP;
import static com.stablebridge.txrecovery.testutil.fixtures.TransactionLifecycleEventFixtures.SOME_FIXED_TIMESTAMP;
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
        var topic = TOPIC_PREFIX + SOME_CHAIN;
        var event = SOME_EVENT;

        // when
        transactionEventPublisher.publish(event);
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
