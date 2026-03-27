package com.stablebridge.txrecovery.infrastructure.stream;

import static com.stablebridge.txrecovery.infrastructure.stream.KafkaTransactionEventPublisher.DLQ_PREFIX;
import static com.stablebridge.txrecovery.infrastructure.stream.KafkaTransactionEventPublisher.TOPIC_PREFIX;
import static com.stablebridge.txrecovery.testutil.fixtures.TransactionLifecycleEventFixtures.SOME_CHAIN;
import static com.stablebridge.txrecovery.testutil.fixtures.TransactionLifecycleEventFixtures.SOME_CHAIN_UPPER;
import static com.stablebridge.txrecovery.testutil.fixtures.TransactionLifecycleEventFixtures.SOME_EVENT;
import static com.stablebridge.txrecovery.testutil.fixtures.TransactionLifecycleEventFixtures.SOME_EVENT_UPPER_CHAIN;
import static com.stablebridge.txrecovery.testutil.fixtures.TransactionLifecycleEventFixtures.SOME_EVENT_WITHOUT_TO_ADDRESS;
import static com.stablebridge.txrecovery.testutil.fixtures.TransactionLifecycleEventFixtures.SOME_INTENT_ID;
import static com.stablebridge.txrecovery.testutil.fixtures.TransactionLifecycleEventFixtures.SOME_PAYLOAD;
import static com.stablebridge.txrecovery.testutil.fixtures.TransactionLifecycleEventFixtures.SOME_TO_ADDRESS;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import java.util.concurrent.CompletableFuture;

import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import com.stablebridge.txrecovery.domain.exception.EventSerializationException;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class KafkaTransactionEventPublisherTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private KafkaTransactionEventPublisher publisher;

    @Test
    void shouldPublishToCorrectTopicWithToAddressAsKey() {
        // given
        var event = SOME_EVENT;
        var expectedTopic = TOPIC_PREFIX + SOME_CHAIN;
        given(objectMapper.writeValueAsString(event)).willReturn(SOME_PAYLOAD);
        given(kafkaTemplate.send(expectedTopic, SOME_TO_ADDRESS, SOME_PAYLOAD))
                .willReturn(completedFuture());

        // when
        publisher.publish(event);

        // then
        then(kafkaTemplate).should().send(expectedTopic, SOME_TO_ADDRESS, SOME_PAYLOAD);
    }

    @Test
    void shouldFallBackToIntentIdAsKey_whenToAddressIsNull() {
        // given
        var event = SOME_EVENT_WITHOUT_TO_ADDRESS;
        var expectedTopic = TOPIC_PREFIX + SOME_CHAIN;
        given(objectMapper.writeValueAsString(event)).willReturn(SOME_PAYLOAD);
        given(kafkaTemplate.send(expectedTopic, SOME_INTENT_ID, SOME_PAYLOAD))
                .willReturn(completedFuture());

        // when
        publisher.publish(event);

        // then
        then(kafkaTemplate).should().send(expectedTopic, SOME_INTENT_ID, SOME_PAYLOAD);
    }

    @Test
    void shouldResolveLowercaseTopicFromChainName() {
        // given
        var event = SOME_EVENT_UPPER_CHAIN;
        var expectedTopic = TOPIC_PREFIX + SOME_CHAIN_UPPER.toLowerCase();
        var expectedPayload = "{}";
        given(objectMapper.writeValueAsString(event)).willReturn(expectedPayload);
        given(kafkaTemplate.send(expectedTopic, SOME_INTENT_ID, expectedPayload))
                .willReturn(completedFuture());

        // when
        publisher.publish(event);

        // then
        then(kafkaTemplate).should().send(expectedTopic, SOME_INTENT_ID, expectedPayload);
    }

    @Test
    void shouldPublishToDlq_whenSendFails() {
        // given
        var event = SOME_EVENT;
        var expectedTopic = TOPIC_PREFIX + SOME_CHAIN;
        var expectedDlqTopic = DLQ_PREFIX + SOME_CHAIN;
        given(objectMapper.writeValueAsString(event)).willReturn(SOME_PAYLOAD);
        given(kafkaTemplate.send(expectedTopic, SOME_TO_ADDRESS, SOME_PAYLOAD))
                .willReturn(failedFuture());
        given(kafkaTemplate.send(expectedDlqTopic, SOME_TO_ADDRESS, SOME_PAYLOAD))
                .willReturn(completedFuture());

        // when
        publisher.publish(event);

        // then
        then(kafkaTemplate).should().send(expectedDlqTopic, SOME_TO_ADDRESS, SOME_PAYLOAD);
    }

    @Test
    void shouldThrowEventSerializationException_whenSerializationFails() {
        // given
        var event = SOME_EVENT;
        given(objectMapper.writeValueAsString(event)).willThrow(JacksonException.class);

        // when/then
        assertThatThrownBy(() -> publisher.publish(event))
                .isInstanceOf(EventSerializationException.class);
        then(kafkaTemplate).should(never()).send(TOPIC_PREFIX + SOME_CHAIN, SOME_TO_ADDRESS, SOME_PAYLOAD);
    }

    private CompletableFuture<SendResult<String, String>> completedFuture() {
        var metadata = new RecordMetadata(new TopicPartition("topic", 0), 0, 0, 0, 0, 0);
        var result = new SendResult<String, String>(null, metadata);
        return CompletableFuture.completedFuture(result);
    }

    private CompletableFuture<SendResult<String, String>> failedFuture() {
        return CompletableFuture.failedFuture(new RuntimeException("Kafka unavailable"));
    }
}
