package com.stablebridge.txrecovery.infrastructure.metrics;

import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class RecoveryMetrics {

    private static final String ATTEMPTS_TOTAL_METRIC = "str.recovery.attempts.total";
    private static final String GAS_SPENT_TOTAL_METRIC = "str.recovery.gas.spent.total";

    private final MeterRegistry registry;

    public void recordAttempt(String chain, String action, String outcome) {
        Counter.builder(ATTEMPTS_TOTAL_METRIC)
                .tag("chain", chain)
                .tag("action", action)
                .tag("outcome", outcome)
                .register(registry)
                .increment();
    }

    public void recordGasSpent(String chain, String denomination, double amount) {
        Counter.builder(GAS_SPENT_TOTAL_METRIC)
                .tag("chain", chain)
                .tag("denomination", denomination)
                .register(registry)
                .increment(amount);
    }
}
