package com.stablebridge.txrecovery.domain.transaction.event;

import static com.stablebridge.txrecovery.domain.transaction.model.TransactionStatus.RECEIVED;
import static com.stablebridge.txrecovery.domain.transaction.model.TransactionStatus.SIGNING;
import static com.stablebridge.txrecovery.domain.transaction.model.TransactionStatus.SUBMITTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

class TransactionLifecycleEventTest {

    @Test
    void shouldModifyStatusViaToBuilder() {
        // given
        var now = Instant.now();
        var event = TransactionLifecycleEvent.builder()
                .eventId("evt-001")
                .intentId("intent-001")
                .transactionHash("0xabc123")
                .chain("ethereum")
                .status(SIGNING)
                .timestamp(now)
                .metadata(Map.of("gasPrice", "50gwei"))
                .build();

        // when
        var modified = event.toBuilder()
                .status(SUBMITTED)
                .previousStatus(SIGNING)
                .build();

        // then
        var expected = TransactionLifecycleEvent.builder()
                .eventId("evt-001")
                .intentId("intent-001")
                .transactionHash("0xabc123")
                .chain("ethereum")
                .status(SUBMITTED)
                .previousStatus(SIGNING)
                .timestamp(now)
                .metadata(Map.of("gasPrice", "50gwei"))
                .build();
        assertThat(modified).usingRecursiveComparison().isEqualTo(expected);
    }

    @Test
    void shouldDefaultOptionalFieldsToNull() {
        // when
        var event = TransactionLifecycleEvent.builder()
                .eventId("evt-002")
                .intentId("intent-002")
                .chain("base")
                .status(RECEIVED)
                .timestamp(Instant.now())
                .build();

        // then
        assertThat(event.transactionHash()).isNull();
        assertThat(event.toAddress()).isNull();
        assertThat(event.previousStatus()).isNull();
        assertThat(event.metadata()).isEmpty();
    }

    @Test
    void shouldThrowNullPointerException_whenEventIdIsNull() {
        // when / then
        assertThatThrownBy(() -> TransactionLifecycleEvent.builder()
                .eventId(null)
                .intentId("intent-001")
                .chain("ethereum")
                .status(RECEIVED)
                .timestamp(Instant.now())
                .build())
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldThrowNullPointerException_whenIntentIdIsNull() {
        // when / then
        assertThatThrownBy(() -> TransactionLifecycleEvent.builder()
                .eventId("evt-001")
                .intentId(null)
                .chain("ethereum")
                .status(RECEIVED)
                .timestamp(Instant.now())
                .build())
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldThrowNullPointerException_whenChainIsNull() {
        // when / then
        assertThatThrownBy(() -> TransactionLifecycleEvent.builder()
                .eventId("evt-001")
                .intentId("intent-001")
                .chain(null)
                .status(RECEIVED)
                .timestamp(Instant.now())
                .build())
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldThrowNullPointerException_whenStatusIsNull() {
        // when / then
        assertThatThrownBy(() -> TransactionLifecycleEvent.builder()
                .eventId("evt-001")
                .intentId("intent-001")
                .chain("ethereum")
                .status(null)
                .timestamp(Instant.now())
                .build())
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldThrowNullPointerException_whenTimestampIsNull() {
        // when / then
        assertThatThrownBy(() -> TransactionLifecycleEvent.builder()
                .eventId("evt-001")
                .intentId("intent-001")
                .chain("ethereum")
                .status(RECEIVED)
                .timestamp(null)
                .build())
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldDefensivelyCopyMetadata() {
        // given
        var mutableMap = new HashMap<String, String>();
        mutableMap.put("key", "value");

        // when
        var event = TransactionLifecycleEvent.builder()
                .eventId("evt-001")
                .intentId("intent-001")
                .chain("ethereum")
                .status(RECEIVED)
                .timestamp(Instant.now())
                .metadata(mutableMap)
                .build();

        mutableMap.put("key2", "value2");

        // then
        assertThat(event.metadata()).hasSize(1).containsEntry("key", "value");
    }
}
