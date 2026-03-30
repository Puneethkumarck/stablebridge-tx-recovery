package com.stablebridge.txrecovery.api.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

import lombok.Builder;

@Builder(toBuilder = true)
public record GasHistoryEntry(
        Instant timestamp,
        BigDecimal baseFee,
        BigDecimal avgPriorityFee,
        BigDecimal blockUtilization) {

    public GasHistoryEntry {
        Objects.requireNonNull(timestamp, "timestamp");
        Objects.requireNonNull(baseFee, "baseFee");
        Objects.requireNonNull(avgPriorityFee, "avgPriorityFee");
        Objects.requireNonNull(blockUtilization, "blockUtilization");
    }
}
