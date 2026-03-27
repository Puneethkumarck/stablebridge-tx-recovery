package com.stablebridge.txrecovery.application.config;

import static com.stablebridge.txrecovery.infrastructure.stream.KafkaTransactionEventPublisher.DLQ_PREFIX;
import static com.stablebridge.txrecovery.infrastructure.stream.KafkaTransactionEventPublisher.TOPIC_PREFIX;

import java.util.HashMap;
import java.util.List;
import java.util.stream.Stream;

import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import com.stablebridge.txrecovery.domain.transaction.port.TransactionEventPublisher;
import com.stablebridge.txrecovery.infrastructure.stream.KafkaTransactionEventPublisher;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

@Configuration
@Slf4j
@ConditionalOnProperty(name = "spring.kafka.bootstrap-servers")
public class KafkaConfig {

    static final int PARTITIONS = 6;
    static final int RETENTION_DAYS = 30;

    @Bean
    KafkaAdmin kafkaAdmin(@Value("${spring.kafka.bootstrap-servers}") String bootstrapServers) {
        var configs = new HashMap<String, Object>();
        configs.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        return new KafkaAdmin(configs);
    }

    @Bean
    ProducerFactory<String, String> kafkaProducerFactory(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers) {
        var props = new HashMap<String, Object>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.RETRIES_CONFIG, 3);
        log.info("Configuring Kafka producer factory with bootstrap servers: {}", bootstrapServers);
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    KafkaTemplate<String, String> kafkaTemplate(ProducerFactory<String, String> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }

    @Bean
    KafkaAdmin.NewTopics chainTopics(
            @Value("${str.kafka.enabled-chains:}") List<String> enabledChains) {
        var chains = enabledChains.stream().filter(s -> !s.isBlank()).toList();

        var topics = chains.stream()
                .flatMap(chain -> Stream.of(buildEventTopic(chain), buildDlqTopic(chain)))
                .toArray(NewTopic[]::new);

        log.info("Creating Kafka topics for chains: {}", chains);
        return new KafkaAdmin.NewTopics(topics);
    }

    @Bean
    TransactionEventPublisher kafkaTransactionEventPublisher(
            KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        return new KafkaTransactionEventPublisher(kafkaTemplate, objectMapper);
    }

    private NewTopic buildEventTopic(String chain) {
        return TopicBuilder.name(TOPIC_PREFIX + chain)
                .partitions(PARTITIONS)
                .replicas(1)
                .config("retention.ms", String.valueOf((long) RETENTION_DAYS * 24 * 60 * 60 * 1000))
                .config("cleanup.policy", "delete")
                .build();
    }

    private NewTopic buildDlqTopic(String chain) {
        return TopicBuilder.name(DLQ_PREFIX + chain)
                .partitions(PARTITIONS)
                .replicas(1)
                .config("cleanup.policy", "delete")
                .build();
    }
}
