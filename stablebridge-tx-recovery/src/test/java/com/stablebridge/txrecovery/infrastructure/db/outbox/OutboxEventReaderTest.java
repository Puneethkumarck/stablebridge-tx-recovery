package com.stablebridge.txrecovery.infrastructure.db.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import com.stablebridge.txrecovery.infrastructure.db.outbox.OutboxEventReader.PendingOutboxEvent;

@ExtendWith(MockitoExtension.class)
class OutboxEventReaderTest {

    private static final String SOME_EVENT_ID = "evt-001";
    private static final UUID SOME_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Instant FIXED_INSTANT = Instant.parse("2026-03-31T12:00:00Z");

    @Mock
    private OutboxEventJpaRepository outboxRepository;

    @Spy
    private final Clock clock = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);

    @InjectMocks
    private OutboxEventReader reader;

    @Nested
    class FindPending {

        @Test
        void shouldMapEntitiesToPendingOutboxEvents() {
            // given
            var entity = buildEntity(SOME_ID, SOME_EVENT_ID);
            given(outboxRepository.findPendingOrderByCreatedAt(10)).willReturn(List.of(entity));

            // when
            var result = reader.findPending(10);

            // then
            var expected = new PendingOutboxEvent(
                    SOME_ID.toString(), SOME_EVENT_ID,
                    "str.tx.events.ethereum_mainnet", "0xrecipient", "{\"json\":true}");
            assertThat(result).hasSize(1);
            assertThat(result.getFirst())
                    .usingRecursiveComparison()
                    .isEqualTo(expected);
        }

        @Test
        void shouldReturnEmptyListWhenNoPendingEvents() {
            // given
            given(outboxRepository.findPendingOrderByCreatedAt(10)).willReturn(List.of());

            // when
            var result = reader.findPending(10);

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    class MarkPublished {

        @Test
        void shouldUpdateStatusToPublished() {
            // when
            reader.markPublished(SOME_EVENT_ID);

            // then
            then(outboxRepository).should().updateStatusAndPublishedAt(
                    SOME_EVENT_ID, OutboxEventStatus.PUBLISHED, FIXED_INSTANT);
        }
    }

    @Nested
    class IncrementRetryOrFail {

        @Test
        void shouldIncrementRetryCount() {
            // given
            var entity = buildEntity(SOME_ID, SOME_EVENT_ID);
            entity.setRetryCount(2);
            given(outboxRepository.findByEventId(SOME_EVENT_ID)).willReturn(Optional.of(entity));

            // when
            reader.incrementRetryOrFail(SOME_EVENT_ID, 5);

            // then
            assertThat(entity.getRetryCount()).isEqualTo(3);
            assertThat(entity.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
            then(outboxRepository).should().save(entity);
        }

        @Test
        void shouldMarkAsFailedWhenMaxRetriesReached() {
            // given
            var entity = buildEntity(SOME_ID, SOME_EVENT_ID);
            entity.setRetryCount(4);
            given(outboxRepository.findByEventId(SOME_EVENT_ID)).willReturn(Optional.of(entity));

            // when
            reader.incrementRetryOrFail(SOME_EVENT_ID, 5);

            // then
            assertThat(entity.getRetryCount()).isEqualTo(5);
            assertThat(entity.getStatus()).isEqualTo(OutboxEventStatus.FAILED);
            then(outboxRepository).should().save(entity);
        }

        @Test
        void shouldDoNothingWhenEventNotFound() {
            // given
            given(outboxRepository.findByEventId(SOME_EVENT_ID)).willReturn(Optional.empty());

            // when
            reader.incrementRetryOrFail(SOME_EVENT_ID, 5);

            // then
            then(outboxRepository).should().findByEventId(SOME_EVENT_ID);
            then(outboxRepository).shouldHaveNoMoreInteractions();
        }
    }

    private OutboxEventEntity buildEntity(UUID id, String eventId) {
        var entity = new OutboxEventEntity();
        entity.setId(id);
        entity.setEventId(eventId);
        entity.setIntentId("intent-001");
        entity.setTopic("str.tx.events.ethereum_mainnet");
        entity.setPartitionKey("0xrecipient");
        entity.setPayload("{\"json\":true}");
        entity.setStatus(OutboxEventStatus.PENDING);
        entity.setCreatedAt(Instant.now());
        entity.setRetryCount(0);
        return entity;
    }
}
