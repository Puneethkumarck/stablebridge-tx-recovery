package com.stablebridge.txrecovery.infrastructure.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class EscalationMetricsTest {

    private MeterRegistry registry;
    private EscalationMetrics escalationMetrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        escalationMetrics = new EscalationMetrics(registry);
    }

    @Test
    void shouldRecordEscalation() {
        // given
        var chain = "ethereum";

        // when
        escalationMetrics.recordEscalation(chain);

        // then
        var counter = registry.get("str.human.escalation.total")
                .tags("chain", "ethereum")
                .counter();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    void shouldSetPending() {
        // given
        var chain = "ethereum";

        // when
        escalationMetrics.setPending(chain, 5);

        // then
        var gauge = registry.get("str.human.escalation.pending")
                .tags("chain", "ethereum")
                .gauge();
        assertThat(gauge.value()).isEqualTo(5.0);
    }

    @Test
    void shouldUpdatePending() {
        // given
        var chain = "ethereum";

        // when
        escalationMetrics.setPending(chain, 5);
        escalationMetrics.setPending(chain, 12);

        // then
        var gauge = registry.get("str.human.escalation.pending")
                .tags("chain", "ethereum")
                .gauge();
        assertThat(gauge.value()).isEqualTo(12.0);
    }

    @Test
    void shouldRecordResponseDuration() {
        // given
        var chain = "ethereum";
        var duration = Duration.ofSeconds(30);

        // when
        escalationMetrics.recordResponseDuration(chain, duration);

        // then
        var timer = registry.get("str.human.response.duration.seconds")
                .tags("chain", "ethereum")
                .timer();
        assertThat(timer.count()).isEqualTo(1);
    }

    @Test
    void shouldIncrementEscalationCounterMultipleTimes() {
        // given
        var chain = "ethereum";

        // when
        escalationMetrics.recordEscalation(chain);
        escalationMetrics.recordEscalation(chain);

        // then
        var counter = registry.get("str.human.escalation.total")
                .tags("chain", "ethereum")
                .counter();
        assertThat(counter.count()).isEqualTo(2.0);
    }
}
