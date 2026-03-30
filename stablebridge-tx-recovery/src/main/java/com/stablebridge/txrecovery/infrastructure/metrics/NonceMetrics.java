package com.stablebridge.txrecovery.infrastructure.metrics;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;

@Component
public class NonceMetrics {

    private final MeterRegistry registry;
    private final ConcurrentHashMap<Tags, AtomicLong> inFlightGauges = new ConcurrentHashMap<>();

    public NonceMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void recordNonceAllocated(String chain) {
        Counter.builder("str.nonce.allocated.total")
                .tag("chain", chain)
                .register(registry)
                .increment();
    }

    public void recordNonceGapDetected(String chain) {
        Counter.builder("str.nonce.gaps.detected.total")
                .tag("chain", chain)
                .register(registry)
                .increment();
    }

    public void setNonceInFlight(String chain, String address, long count) {
        var tags = Tags.of("chain", chain, "address", address);
        var holder = inFlightGauges.computeIfAbsent(tags, t -> {
            var val = new AtomicLong(0);
            Gauge.builder("str.nonce.in.flight", val, AtomicLong::doubleValue)
                    .tags(t)
                    .register(registry);
            return val;
        });
        holder.set(count);
    }
}
