package com.stablebridge.txrecovery.infrastructure.metrics;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class NonceMetrics {

    private static final String ALLOCATED_TOTAL_METRIC = "str.nonce.allocated.total";
    private static final String GAPS_DETECTED_TOTAL_METRIC = "str.nonce.gaps.detected.total";
    private static final String IN_FLIGHT_METRIC = "str.nonce.in.flight";

    private final MeterRegistry registry;
    private final ConcurrentHashMap<Tags, AtomicLong> inFlightGauges = new ConcurrentHashMap<>();

    public void recordNonceAllocated(String chain) {
        Counter.builder(ALLOCATED_TOTAL_METRIC)
                .tag("chain", chain)
                .register(registry)
                .increment();
    }

    public void recordNonceGapDetected(String chain) {
        Counter.builder(GAPS_DETECTED_TOTAL_METRIC)
                .tag("chain", chain)
                .register(registry)
                .increment();
    }

    public void setNonceInFlight(String chain, String address, long count) {
        var tags = Tags.of("chain", chain, "address", address);
        var holder = inFlightGauges.computeIfAbsent(tags, t -> {
            var val = new AtomicLong(0);
            Gauge.builder(IN_FLIGHT_METRIC, val, AtomicLong::doubleValue)
                    .tags(t)
                    .register(registry);
            return val;
        });
        holder.set(count);
    }
}
