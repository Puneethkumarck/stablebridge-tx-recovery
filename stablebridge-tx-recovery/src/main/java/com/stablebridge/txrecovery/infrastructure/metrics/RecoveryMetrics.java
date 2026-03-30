package com.stablebridge.txrecovery.infrastructure.metrics;

import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class RecoveryMetrics {

    private final MeterRegistry registry;

    public void recordAttempt(String chain, String action, String outcome) {
        Counter.builder("str.recovery.attempts.total")
                .tag("chain", chain)
                .tag("action", action)
                .tag("outcome", outcome)
                .register(registry)
                .increment();
    }

    public void recordGasSpent(String chain, String denomination, double amount) {
        Counter.builder("str.recovery.gas.spent.total")
                .tag("chain", chain)
                .tag("denomination", denomination)
                .register(registry)
                .increment(amount);
    }
}
