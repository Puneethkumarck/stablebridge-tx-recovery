package com.stablebridge.txrecovery.infrastructure.metrics;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;

@Component
public class AddressPoolMetrics {

    private static final String POOL_SIZE_METRIC = "str.address.pool.size";
    private static final String IN_FLIGHT_METRIC = "str.address.pool.in.flight";

    private final MeterRegistry registry;
    private final ConcurrentHashMap<Tags, AtomicLong> poolSizeGauges = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Tags, AtomicLong> inFlightGauges = new ConcurrentHashMap<>();

    public AddressPoolMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void setPoolSize(String chain, String tier, String status, long size) {
        var tags = Tags.of("chain", chain, "tier", tier, "status", status);
        var holder = poolSizeGauges.computeIfAbsent(tags, t -> {
            var val = new AtomicLong(0);
            Gauge.builder(POOL_SIZE_METRIC, val, AtomicLong::doubleValue)
                    .tags(t)
                    .register(registry);
            return val;
        });
        holder.set(size);
    }

    public void setInFlight(String chain, String address, long count) {
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
