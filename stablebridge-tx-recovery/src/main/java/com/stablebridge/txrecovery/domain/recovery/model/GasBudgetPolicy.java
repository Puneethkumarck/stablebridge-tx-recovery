package com.stablebridge.txrecovery.domain.recovery.model;

import java.math.BigDecimal;
import java.util.Objects;

import lombok.Builder;

@Builder(toBuilder = true)
public record GasBudgetPolicy(
        BigDecimal percentage,
        BigDecimal absoluteMinUsd,
        BigDecimal absoluteMaxUsd) {

    public GasBudgetPolicy {
        Objects.requireNonNull(percentage);
        Objects.requireNonNull(absoluteMinUsd);
        Objects.requireNonNull(absoluteMaxUsd);
    }

    public BigDecimal calculateBudget(BigDecimal txValueUsd) {
        var percentBudget = txValueUsd.multiply(percentage);
        return percentBudget.max(absoluteMinUsd).min(absoluteMaxUsd);
    }
}
