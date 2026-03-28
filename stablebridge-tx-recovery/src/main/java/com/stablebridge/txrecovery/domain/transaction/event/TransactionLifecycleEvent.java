package com.stablebridge.txrecovery.domain.transaction.event;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

import com.stablebridge.txrecovery.domain.transaction.model.TransactionStatus;

import lombok.Builder;

@Builder(toBuilder = true)
public record TransactionLifecycleEvent(
        String eventId,
        String intentId,
        String transactionHash,
        String toAddress,
        String chain,
        TransactionStatus status,
        TransactionStatus previousStatus,
        Instant timestamp,
        Map<String, String> metadata) {

    public static final String TOPIC_PREFIX = "str.tx.events";

    public TransactionLifecycleEvent {
        Objects.requireNonNull(eventId);
        Objects.requireNonNull(intentId);
        Objects.requireNonNull(chain);
        Objects.requireNonNull(status);
        Objects.requireNonNull(timestamp);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
