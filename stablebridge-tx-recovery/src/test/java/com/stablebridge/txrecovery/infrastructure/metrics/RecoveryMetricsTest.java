package com.stablebridge.txrecovery.infrastructure.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class RecoveryMetricsTest {

    private MeterRegistry registry;
    private RecoveryMetrics recoveryMetrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        recoveryMetrics = new RecoveryMetrics(registry);
    }

    @Test
    void shouldRecordRecoveryAttempt() {
        // given
        var chain = "ethereum";
        var action = "SPEEDUP";
        var outcome = "SUCCESS";

        // when
        recoveryMetrics.recordAttempt(chain, action, outcome);

        // then
        var count = registry.get("str.recovery.attempts.total")
                .tags("chain", "ethereum", "action", "SPEEDUP", "outcome", "SUCCESS")
                .counter()
                .count();
        assertThat(count).isEqualTo(1.0);
    }

    @Test
    void shouldRecordGasSpent() {
        // given
        var chain = "ethereum";
        var denomination = "gwei";
        var amount = 21000.0;

        // when
        recoveryMetrics.recordGasSpent(chain, denomination, amount);

        // then
        var count = registry.get("str.recovery.gas.spent.total")
                .tags("chain", "ethereum", "denomination", "gwei")
                .counter()
                .count();
        assertThat(count).isEqualTo(21000.0);
    }

    @Test
    void shouldAccumulateGasSpent() {
        // given
        var chain = "ethereum";
        var denomination = "gwei";

        // when
        recoveryMetrics.recordGasSpent(chain, denomination, 21000.0);
        recoveryMetrics.recordGasSpent(chain, denomination, 42000.0);

        // then
        var count = registry.get("str.recovery.gas.spent.total")
                .tags("chain", "ethereum", "denomination", "gwei")
                .counter()
                .count();
        assertThat(count).isEqualTo(63000.0);
    }
}
