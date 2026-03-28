package com.stablebridge.txrecovery.domain.recovery.service;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;

import com.stablebridge.txrecovery.domain.recovery.model.EscalationPolicy;
import com.stablebridge.txrecovery.domain.recovery.model.EscalationResult;
import com.stablebridge.txrecovery.domain.recovery.model.EscalationTier;
import com.stablebridge.txrecovery.domain.recovery.model.GasBudgetPolicy;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class EscalationPolicyEngine {

    private final EscalationPolicy defaultPolicy;
    private final EscalationPolicy highValuePolicy;
    private final BigDecimal highValueThresholdUsd;
    private final GasBudgetPolicy gasBudgetPolicy;
    private final Map<String, EscalationPolicy> chainOverrides;

    private record NamedPolicy(String name, EscalationPolicy policy) {}

    public EscalationResult evaluate(
            String chain,
            BigDecimal txValueUsd,
            Duration stuckDuration,
            BigDecimal cumulativeGasSpendUsd) {

        var namedPolicy = selectPolicy(chain, txValueUsd);
        var tier = determineTier(namedPolicy.policy(), stuckDuration);
        var gasBudget = gasBudgetPolicy.calculateBudget(txValueUsd);
        var remainingBudget = gasBudget.subtract(cumulativeGasSpendUsd);
        var budgetExhausted = remainingBudget.compareTo(BigDecimal.ZERO) <= 0;

        var effectiveTier = budgetExhausted && !tier.requiresHumanApproval()
                ? tier.toBuilder().requiresHumanApproval(true).build()
                : tier;

        log.info("Escalation policy={} tier={} level={} budgetExhausted={} chain={} txValueUsd={}",
                namedPolicy.name(), effectiveTier.description(), effectiveTier.level(),
                budgetExhausted, chain, txValueUsd);

        return EscalationResult.builder()
                .selectedTier(effectiveTier)
                .policyName(namedPolicy.name())
                .budgetExhausted(budgetExhausted)
                .remainingBudget(remainingBudget)
                .gasBudget(gasBudget)
                .build();
    }

    private NamedPolicy selectPolicy(String chain, BigDecimal txValueUsd) {
        if (txValueUsd.compareTo(highValueThresholdUsd) > 0) {
            return new NamedPolicy("high-value", highValuePolicy);
        }
        return Optional.ofNullable(chainOverrides.get(chain))
                .map(policy -> new NamedPolicy("chain-override:" + chain, policy))
                .orElseGet(() -> new NamedPolicy("default", defaultPolicy));
    }

    private EscalationTier determineTier(EscalationPolicy policy, Duration stuckDuration) {
        return policy.tiers().stream()
                .filter(tier -> stuckDuration.compareTo(tier.stuckThreshold()) >= 0)
                .max(Comparator.comparingInt(EscalationTier::level))
                .orElse(policy.tiers().getFirst());
    }
}
