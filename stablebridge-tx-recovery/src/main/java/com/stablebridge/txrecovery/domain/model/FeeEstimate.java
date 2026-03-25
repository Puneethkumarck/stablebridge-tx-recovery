package com.stablebridge.txrecovery.domain.model;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;

import lombok.Builder;

@Builder
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
    }
}
