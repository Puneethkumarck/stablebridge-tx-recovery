package com.stablebridge.txrecovery.infrastructure.stream;

import static com.stablebridge.txrecovery.testutil.fixtures.TransactionLifecycleEventFixtures.SOME_EVENT_ID;
import static com.stablebridge.txrecovery.testutil.fixtures.TransactionLifecycleEventFixtures.SOME_PAYLOAD;
import static com.stablebridge.txrecovery.testutil.fixtures.TransactionLifecycleEventFixtures.SOME_TO_ADDRESS;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import java.util.List;
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

import com.stablebridge.txrecovery.infrastructure.db.outbox.OutboxEventReader;
import com.stablebridge.txrecovery.infrastructure.db.outbox.OutboxEventReader.PendingOutboxEvent;

@ExtendWith(MockitoExtension.class)
class OutboxEventRelayTest {

    private static final String SOME_TOPIC = "str.tx.events.ethereum_mainnet";

    @Mock
    private OutboxEventReader outboxEventReader;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @InjectMocks
    private OutboxEventRelay relay;

    @Test
    void shouldRelayPendingEventsToKafkaAndMarkPublished() {
        // given
        var event = new PendingOutboxEvent("id-1", SOME_EVENT_ID, SOME_TOPIC, SOME_TO_ADDRESS, SOME_PAYLOAD);
        given(outboxEventReader.findPending(OutboxEventRelay.BATCH_SIZE)).willReturn(List.of(event));
        given(kafkaTemplate.send(SOME_TOPIC, SOME_TO_ADDRESS, SOME_PAYLOAD)).willReturn(completedFuture());

        // when
        relay.relay();

        // then
        then(outboxEventReader).should().markPublished(SOME_EVENT_ID);
    }

    @Test
    void shouldIncrementRetryCountWhenKafkaSendFails() {
        // given
        var event = new PendingOutboxEvent("id-1", SOME_EVENT_ID, SOME_TOPIC, SOME_TO_ADDRESS, SOME_PAYLOAD);
        given(outboxEventReader.findPending(OutboxEventRelay.BATCH_SIZE)).willReturn(List.of(event));
        given(kafkaTemplate.send(SOME_TOPIC, SOME_TO_ADDRESS, SOME_PAYLOAD)).willReturn(failedFuture());

        // when
        relay.relay();

        // then
        then(outboxEventReader).should(never()).markPublished(SOME_EVENT_ID);
        then(outboxEventReader).should().incrementRetryOrFail(SOME_EVENT_ID, OutboxEventRelay.MAX_RETRIES);
    }

    @Test
    void shouldDoNothingWhenNoPendingEvents() {
        // given
        given(outboxEventReader.findPending(OutboxEventRelay.BATCH_SIZE)).willReturn(List.of());

        // when
        relay.relay();

        // then
        then(kafkaTemplate).shouldHaveNoInteractions();
        then(outboxEventReader).should(never()).markPublished(SOME_EVENT_ID);
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
