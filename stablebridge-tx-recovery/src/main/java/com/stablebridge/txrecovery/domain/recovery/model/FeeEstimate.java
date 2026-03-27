package com.stablebridge.txrecovery.domain.recovery.model;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;

import lombok.Builder;

@Builder(toBuilder = true)
public record FeeEstimate(
        BigDecimal maxFeePerGas,
        BigDecimal maxPriorityFeePerGas,
        BigDecimal computeUnitPrice,
        BigDecimal estimatedCost,
        String denomination,
        FeeUrgency urgency,
        Map<String, String> details) {

    public FeeEstimate {
        Objects.requireNonNull(estimatedCost);
        Objects.requireNonNull(denomination);
        Objects.requireNonNull(urgency);
        details = details == null ? Map.of() : Map.copyOf(details);
    }
}
