package com.stablebridge.txrecovery.infrastructure.db.outbox;

import static com.stablebridge.txrecovery.testutil.TestUtils.eqIgnoringTimestamps;
import static org.mockito.BDDMockito.then;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OutboxEventPersisterTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-03-31T12:00:00Z");

    @Mock
    private OutboxEventJpaRepository outboxRepository;

    @Spy
    private final Clock clock = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);

    @InjectMocks
    private OutboxEventPersister persister;

    @Test
    void shouldPersistOutboxEventWithPendingStatus() {
        // given
        var eventId = "evt-001";
        var intentId = "intent-001";
        var topic = "str.tx.events.ethereum_mainnet";
        var partitionKey = "0xrecipient";
        var payload = "{\"json\":true}";

        var expected = new OutboxEventEntity();
        expected.setEventId(eventId);
        expected.setIntentId(intentId);
        expected.setTopic(topic);
        expected.setPartitionKey(partitionKey);
        expected.setPayload(payload);
        expected.setStatus(OutboxEventStatus.PENDING);
        expected.setCreatedAt(FIXED_INSTANT);
        expected.setRetryCount(0);

        // when
        persister.persist(eventId, intentId, topic, partitionKey, payload);

        // then
        then(outboxRepository).should().save(eqIgnoringTimestamps(expected));
    }
}
