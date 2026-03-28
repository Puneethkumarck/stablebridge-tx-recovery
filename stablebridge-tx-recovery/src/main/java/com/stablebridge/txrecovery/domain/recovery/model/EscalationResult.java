package com.stablebridge.txrecovery.domain.recovery.model;

import java.math.BigDecimal;
import java.util.Objects;

import lombok.Builder;

@Builder(toBuilder = true)
public record EscalationResult(
        EscalationTier selectedTier,
        String policyName,
        boolean budgetExhausted,
        BigDecimal remainingBudget,
        BigDecimal gasBudget) {

    public EscalationResult {
        Objects.requireNonNull(selectedTier);
        Objects.requireNonNull(policyName);
        Objects.requireNonNull(remainingBudget);
        Objects.requireNonNull(gasBudget);
    }
}
