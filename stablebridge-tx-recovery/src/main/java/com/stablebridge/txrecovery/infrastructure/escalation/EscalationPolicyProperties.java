package com.stablebridge.txrecovery.infrastructure.escalation;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Builder;

@ConfigurationProperties(prefix = "str.escalation")
@Builder(toBuilder = true)
public record EscalationPolicyProperties(
        BigDecimal highValueThresholdUsd,
        GasBudgetConfig gasBudget,
        List<TierConfig> defaultTiers,
        List<TierConfig> highValueTiers,
        Map<String, List<TierConfig>> chainOverrides) {

    public EscalationPolicyProperties {
        Objects.requireNonNull(highValueThresholdUsd);
        Objects.requireNonNull(gasBudget);
        Objects.requireNonNull(defaultTiers);
        Objects.requireNonNull(highValueTiers);
    }

    @Builder(toBuilder = true)
    public record TierConfig(
            int level,
            Duration stuckThreshold,
            BigDecimal gasMultiplier,
            boolean requiresHumanApproval,
            String description) {}

    @Builder(toBuilder = true)
    public record GasBudgetConfig(
            BigDecimal percentage,
            BigDecimal absoluteMinUsd,
            BigDecimal absoluteMaxUsd) {}
}
