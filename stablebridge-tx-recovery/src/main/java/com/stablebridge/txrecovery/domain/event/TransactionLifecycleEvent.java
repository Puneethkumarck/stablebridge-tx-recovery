package com.stablebridge.txrecovery.domain.event;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

import com.stablebridge.txrecovery.domain.model.TransactionStatus;

import lombok.Builder;

@Builder
public record TransactionLifecycleEvent(
        String eventId,
        String intentId,
        String transactionHash,
        String chain,
        TransactionStatus status,
        TransactionStatus previousStatus,
        Instant timestamp,
        Map<String, String> metadata) {

    public TransactionLifecycleEvent {
        Objects.requireNonNull(eventId);
        Objects.requireNonNull(intentId);
        Objects.requireNonNull(chain);
        Objects.requireNonNull(status);
        Objects.requireNonNull(timestamp);
        metadata = metadata == null ? null : Map.copyOf(metadata);
    }
}
