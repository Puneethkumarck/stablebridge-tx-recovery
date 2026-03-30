package com.stablebridge.txrecovery.infrastructure.metrics;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class AddressPoolMetrics {

    private static final String POOL_SIZE_METRIC = "str.address.pool.size";
    private static final String IN_FLIGHT_METRIC = "str.address.pool.in.flight";

    private final MeterRegistry registry;
    private final ConcurrentHashMap<Tags, AtomicLong> poolSizeGauges = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Tags, InFlightHolder> inFlightGauges = new ConcurrentHashMap<>();

    public void setPoolSize(String chain, String tier, String status, long size) {
        if (size < 0) {
            throw new IllegalArgumentException("Pool size must be >= 0, got: " + size);
        }
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
        if (count < 0) {
            throw new IllegalArgumentException("In-flight count must be >= 0, got: " + count);
        }
        var tags = Tags.of("chain", chain, "address", address);
        var holder = inFlightGauges.computeIfAbsent(tags, t -> {
            var val = new AtomicLong(0);
            var meter = Gauge.builder(IN_FLIGHT_METRIC, val, AtomicLong::doubleValue)
                    .tags(t)
                    .register(registry);
            return new InFlightHolder(val, meter);
        });
        holder.value().set(count);
    }

    public void clearInFlight(String chain, String address) {
        var tags = Tags.of("chain", chain, "address", address);
        var holder = inFlightGauges.remove(tags);
        if (holder != null) {
            registry.remove(holder.meter());
        }
    }

    private record InFlightHolder(AtomicLong value, Meter meter) {}
}
