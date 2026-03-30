package com.stablebridge.txrecovery.infrastructure.metrics;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class EscalationMetrics {

    private static final String ESCALATION_TOTAL_METRIC = "str.human.escalation.total";
    private static final String ESCALATION_PENDING_METRIC = "str.human.escalation.pending";
    private static final String RESPONSE_DURATION_METRIC = "str.human.response.duration.seconds";

    private final MeterRegistry registry;
    private final ConcurrentHashMap<Tags, AtomicLong> pendingGauges = new ConcurrentHashMap<>();

    public void recordEscalation(String chain) {
        Counter.builder(ESCALATION_TOTAL_METRIC)
                .tag("chain", chain)
                .register(registry)
                .increment();
    }

    public void setPending(String chain, long count) {
        var tags = Tags.of("chain", chain);
        var holder = pendingGauges.computeIfAbsent(tags, t -> {
            var val = new AtomicLong(0);
            Gauge.builder(ESCALATION_PENDING_METRIC, val, AtomicLong::doubleValue)
                    .tags(t)
                    .register(registry);
            return val;
        });
        holder.set(count);
    }

    public void recordResponseDuration(String chain, Duration duration) {
        Timer.builder(RESPONSE_DURATION_METRIC)
                .tag("chain", chain)
                .publishPercentileHistogram()
                .register(registry)
                .record(duration);
    }
}
