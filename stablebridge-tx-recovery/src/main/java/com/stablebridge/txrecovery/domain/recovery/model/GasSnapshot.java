package com.stablebridge.txrecovery.domain.recovery.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import lombok.Builder;

@Builder(toBuilder = true)
public record GasSnapshot(
        String chain,
        List<FeeEstimate> estimates,
        Instant updatedAt) {

    public GasSnapshot {
        Objects.requireNonNull(chain);
        Objects.requireNonNull(estimates);
        Objects.requireNonNull(updatedAt);
        estimates = List.copyOf(estimates);
    }
}
