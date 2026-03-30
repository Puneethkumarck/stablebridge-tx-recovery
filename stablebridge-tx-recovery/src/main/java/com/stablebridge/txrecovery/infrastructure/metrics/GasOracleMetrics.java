package com.stablebridge.txrecovery.infrastructure.metrics;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class GasOracleMetrics {

    private static final String BASE_FEE_METRIC = "str.gas.base.fee.gwei";
    private static final String ESTIMATE_METRIC = "str.gas.estimate.gwei";

    private final MeterRegistry registry;
    private final ConcurrentHashMap<Tags, AtomicReference<Double>> baseFeeGauges =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Tags, AtomicReference<Double>> estimateGauges =
            new ConcurrentHashMap<>();

    public void setBaseFee(String chain, double gwei) {
        var tags = Tags.of("chain", chain);
        var holder = baseFeeGauges.computeIfAbsent(tags, t -> {
            var val = new AtomicReference<>(0.0);
            Gauge.builder(BASE_FEE_METRIC, val, AtomicReference::get)
                    .tags(t)
                    .register(registry);
            return val;
        });
        holder.set(gwei);
    }

    public void setEstimate(String chain, String urgency, double gwei) {
        var tags = Tags.of("chain", chain, "urgency", urgency);
        var holder = estimateGauges.computeIfAbsent(tags, t -> {
            var val = new AtomicReference<>(0.0);
            Gauge.builder(ESTIMATE_METRIC, val, AtomicReference::get)
                    .tags(t)
                    .register(registry);
            return val;
        });
        holder.set(gwei);
    }
}
