package com.stablebridge.txrecovery.infrastructure.escalation;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import lombok.Builder;

@Validated
@ConfigurationProperties(prefix = "str.escalation")
@Builder(toBuilder = true)
public record EscalationPolicyProperties(
        @NotNull @DecimalMin("0") BigDecimal highValueThresholdUsd,
        @NotNull @Valid GasBudgetConfig gasBudget,
        @NotEmpty @Valid List<TierConfig> defaultTiers,
        @NotEmpty @Valid List<TierConfig> highValueTiers,
        Map<String, @Valid List<TierConfig>> chainOverrides) {

    public EscalationPolicyProperties {
        if (gasBudget != null && gasBudget.absoluteMinUsd() != null && gasBudget.absoluteMaxUsd() != null
                && gasBudget.absoluteMinUsd().compareTo(gasBudget.absoluteMaxUsd()) > 0) {
            throw new IllegalArgumentException("Gas budget absoluteMinUsd must not exceed absoluteMaxUsd");
        }
        if (defaultTiers != null) {
            validateMonotonicTiers(defaultTiers, "defaultTiers");
        }
        if (highValueTiers != null) {
            validateMonotonicTiers(highValueTiers, "highValueTiers");
        }
        if (chainOverrides != null) {
            chainOverrides.forEach((chain, tiers) -> validateMonotonicTiers(tiers, "chainOverrides[" + chain + "]"));
        }
    }

    private static void validateMonotonicTiers(List<TierConfig> tiers, String name) {
        var sorted = tiers.stream()
                .sorted(Comparator.comparingInt(TierConfig::level))
                .toList();
        for (int i = 1; i < sorted.size(); i++) {
            var previous = sorted.get(i - 1);
            var current = sorted.get(i);
            if (current.stuckThreshold() != null && previous.stuckThreshold() != null
                    && current.stuckThreshold().compareTo(previous.stuckThreshold()) < 0) {
                throw new IllegalArgumentException(
                        name + ": tier level " + current.level()
                                + " has stuckThreshold before tier level " + previous.level());
            }
        }
    }

    @Builder(toBuilder = true)
    public record TierConfig(
            @Min(0) int level,
            @NotNull Duration stuckThreshold,
            @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal gasMultiplier,
            boolean requiresHumanApproval,
            @NotBlank String description) {}

    @Builder(toBuilder = true)
    public record GasBudgetConfig(
            @NotNull @DecimalMin("0") BigDecimal percentage,
            @NotNull @DecimalMin("0") BigDecimal absoluteMinUsd,
            @NotNull @DecimalMin("0") BigDecimal absoluteMaxUsd) {}
}
