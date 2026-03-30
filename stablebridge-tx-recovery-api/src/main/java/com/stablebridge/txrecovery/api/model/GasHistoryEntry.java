package com.stablebridge.txrecovery.api.model;

import java.math.BigDecimal;
import java.time.Instant;

import lombok.Builder;

@Builder(toBuilder = true)
public record GasHistoryEntry(
        Instant timestamp,
        BigDecimal baseFee,
        BigDecimal avgPriorityFee,
        BigDecimal blockUtilization) {}
