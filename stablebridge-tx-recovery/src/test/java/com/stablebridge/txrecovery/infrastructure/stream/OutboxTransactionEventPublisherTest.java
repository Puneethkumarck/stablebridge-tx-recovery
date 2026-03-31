package com.stablebridge.txrecovery.infrastructure.stream;

import static com.stablebridge.txrecovery.infrastructure.stream.OutboxTransactionEventPublisher.TOPIC_PREFIX;
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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.stablebridge.txrecovery.domain.exception.EventSerializationException;
import com.stablebridge.txrecovery.infrastructure.db.outbox.OutboxEventPersister;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class OutboxTransactionEventPublisherTest {

    @Mock
    private OutboxEventPersister outboxEventPersister;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private OutboxTransactionEventPublisher publisher;

    @Test
    void shouldPersistToOutboxWithCorrectTopicAndToAddressAsKey() {
        // given
        var event = SOME_EVENT;
        var expectedTopic = TOPIC_PREFIX + SOME_CHAIN;
        given(objectMapper.writeValueAsString(event)).willReturn(SOME_PAYLOAD);

        // when
        publisher.publish(event);

        // then
        then(outboxEventPersister).should().persist(
                event.eventId(), event.intentId(), expectedTopic, SOME_TO_ADDRESS, SOME_PAYLOAD);
    }

    @Test
    void shouldFallBackToIntentIdAsKey_whenToAddressIsNull() {
        // given
        var event = SOME_EVENT_WITHOUT_TO_ADDRESS;
        var expectedTopic = TOPIC_PREFIX + SOME_CHAIN;
        given(objectMapper.writeValueAsString(event)).willReturn(SOME_PAYLOAD);

        // when
        publisher.publish(event);

        // then
        then(outboxEventPersister).should().persist(
                event.eventId(), event.intentId(), expectedTopic, SOME_INTENT_ID, SOME_PAYLOAD);
    }

    @Test
    void shouldResolveLowercaseTopicFromChainName() {
        // given
        var event = SOME_EVENT_UPPER_CHAIN;
        var expectedTopic = TOPIC_PREFIX + SOME_CHAIN_UPPER.toLowerCase();
        var expectedPayload = "{}";
        given(objectMapper.writeValueAsString(event)).willReturn(expectedPayload);

        // when
        publisher.publish(event);

        // then
        then(outboxEventPersister).should().persist(
                event.eventId(), event.intentId(), expectedTopic, SOME_INTENT_ID, expectedPayload);
    }

    @Test
    void shouldThrowEventSerializationException_whenSerializationFails() {
        // given
        var event = SOME_EVENT;
        given(objectMapper.writeValueAsString(event)).willThrow(JacksonException.class);

        // when/then
        assertThatThrownBy(() -> publisher.publish(event))
                .isInstanceOf(EventSerializationException.class);
        then(outboxEventPersister).shouldHaveNoInteractions();
    }
}
