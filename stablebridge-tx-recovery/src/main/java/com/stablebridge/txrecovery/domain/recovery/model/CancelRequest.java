package com.stablebridge.txrecovery.domain.recovery.model;

import java.time.Instant;
import java.util.Objects;

import lombok.Builder;

@Builder(toBuilder = true)
public record CancelRequest(
        String requestedBy,
        String reason,
        Instant requestedAt) {

    public CancelRequest {
        Objects.requireNonNull(requestedBy);
    }
}
