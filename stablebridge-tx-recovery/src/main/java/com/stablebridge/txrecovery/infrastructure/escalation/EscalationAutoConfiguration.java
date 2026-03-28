package com.stablebridge.txrecovery.infrastructure.escalation;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.stablebridge.txrecovery.domain.recovery.model.EscalationPolicy;
import com.stablebridge.txrecovery.domain.recovery.model.EscalationTier;
import com.stablebridge.txrecovery.domain.recovery.model.GasBudgetPolicy;
import com.stablebridge.txrecovery.domain.recovery.service.EscalationPolicyEngine;
import com.stablebridge.txrecovery.infrastructure.escalation.EscalationPolicyProperties.GasBudgetConfig;
import com.stablebridge.txrecovery.infrastructure.escalation.EscalationPolicyProperties.TierConfig;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
@EnableConfigurationProperties(EscalationPolicyProperties.class)
class EscalationAutoConfiguration {

    @Bean
    EscalationPolicyEngine escalationPolicyEngine(EscalationPolicyProperties properties) {
        var defaultPolicy = toPolicy(properties.defaultTiers());
        var highValuePolicy = toPolicy(properties.highValueTiers());
        var gasBudgetPolicy = toGasBudgetPolicy(properties.gasBudget());
        var chainOverrides = Optional.ofNullable(properties.chainOverrides())
                .map(overrides -> overrides.entrySet().stream()
                        .collect(Collectors.toMap(Map.Entry::getKey, entry -> toPolicy(entry.getValue()))))
                .orElse(Map.of());

        log.info("Escalation policy engine configured with {} default tiers, {} high-value tiers, {} chain overrides",
                properties.defaultTiers().size(),
                properties.highValueTiers().size(),
                chainOverrides.size());

        return new EscalationPolicyEngine(
                defaultPolicy,
                highValuePolicy,
                properties.highValueThresholdUsd(),
                gasBudgetPolicy,
                chainOverrides);
    }

    private static EscalationPolicy toPolicy(List<TierConfig> tierConfigs) {
        var tiers = tierConfigs.stream()
                .map(EscalationAutoConfiguration::toTier)
                .toList();
        return EscalationPolicy.builder().tiers(tiers).build();
    }

    private static EscalationTier toTier(TierConfig config) {
        return EscalationTier.builder()
                .level(config.level())
                .stuckThreshold(config.stuckThreshold())
                .gasMultiplier(config.gasMultiplier())
                .requiresHumanApproval(config.requiresHumanApproval())
                .description(config.description())
                .build();
    }

    private static GasBudgetPolicy toGasBudgetPolicy(GasBudgetConfig config) {
        return GasBudgetPolicy.builder()
                .percentage(config.percentage())
                .absoluteMinUsd(config.absoluteMinUsd())
                .absoluteMaxUsd(config.absoluteMaxUsd())
                .build();
    }
}
