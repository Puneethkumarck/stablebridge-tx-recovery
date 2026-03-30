package com.stablebridge.txrecovery.infrastructure.metrics;

import java.time.Duration;

import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class TransactionMetrics {

    private final MeterRegistry registry;

    public void recordSubmitted(String chain, String strategy) {
        Counter.builder("str.transactions.submitted.total")
                .tag("chain", chain)
                .tag("strategy", strategy)
                .register(registry)
                .increment();
    }

    public void recordConfirmed(String chain) {
        Counter.builder("str.transactions.confirmed.total")
                .tag("chain", chain)
                .register(registry)
                .increment();
    }

    public void recordStuck(String chain) {
        Counter.builder("str.transactions.stuck.total")
                .tag("chain", chain)
                .register(registry)
                .increment();
    }

    public void recordRecovered(String chain, String action) {
        Counter.builder("str.transactions.recovered.total")
                .tag("chain", chain)
                .tag("action", action)
                .register(registry)
                .increment();
    }

    public void recordFailed(String chain, String reason) {
        Counter.builder("str.transactions.failed.total")
                .tag("chain", chain)
                .tag("reason", reason)
                .register(registry)
                .increment();
    }

    public void recordCancelled(String chain) {
        Counter.builder("str.transactions.cancelled.total")
                .tag("chain", chain)
                .register(registry)
                .increment();
    }

    public void recordConfirmationDuration(String chain, Duration duration) {
        Timer.builder("str.transaction.confirmation.duration.seconds")
                .tag("chain", chain)
                .publishPercentileHistogram()
                .register(registry)
                .record(duration);
    }

    public void recordStuckDuration(String chain, Duration duration) {
        Timer.builder("str.transaction.stuck.duration.seconds")
                .tag("chain", chain)
                .publishPercentileHistogram()
                .register(registry)
                .record(duration);
    }
}
