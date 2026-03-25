package com.stablebridge.txrecovery.domain.model;

import java.time.Instant;
import java.util.Objects;

import lombok.Builder;

@Builder
public record CancelRequest(
        String requestedBy,
        String reason,
        Instant requestedAt) {

    public CancelRequest {
        Objects.requireNonNull(requestedBy);
    }
}
