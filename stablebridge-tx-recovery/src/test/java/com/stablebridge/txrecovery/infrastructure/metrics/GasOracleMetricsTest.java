package com.stablebridge.txrecovery.infrastructure.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class GasOracleMetricsTest {

    private MeterRegistry registry;
    private GasOracleMetrics gasOracleMetrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        gasOracleMetrics = new GasOracleMetrics(registry);
    }

    @Nested
    class BaseFee {

        @Test
        void shouldSetBaseFee() {
            // given
            var chain = "ethereum";

            // when
            gasOracleMetrics.setBaseFee(chain, 25.5);

            // then
            var value = registry.get("str.gas.base.fee.gwei")
                    .tags("chain", "ethereum")
                    .gauge()
                    .value();
            assertThat(value).isEqualTo(25.5);
        }

        @Test
        void shouldUpdateBaseFee() {
            // given
            var chain = "ethereum";
            gasOracleMetrics.setBaseFee(chain, 25.5);

            // when
            gasOracleMetrics.setBaseFee(chain, 30.0);

            // then
            var value = registry.get("str.gas.base.fee.gwei")
                    .tags("chain", "ethereum")
                    .gauge()
                    .value();
            assertThat(value).isEqualTo(30.0);
        }
    }

    @Nested
    class Estimate {

        @Test
        void shouldSetEstimate() {
            // given
            var chain = "ethereum";
            var urgency = "FAST";

            // when
            gasOracleMetrics.setEstimate(chain, urgency, 50.0);

            // then
            var value = registry.get("str.gas.estimate.gwei")
                    .tags("chain", "ethereum", "urgency", "FAST")
                    .gauge()
                    .value();
            assertThat(value).isEqualTo(50.0);
        }

        @Test
        void shouldTrackMultipleUrgencies() {
            // given
            var chain = "ethereum";

            // when
            gasOracleMetrics.setEstimate(chain, "SLOW", 20.0);
            gasOracleMetrics.setEstimate(chain, "FAST", 50.0);

            // then
            var slowValue = registry.get("str.gas.estimate.gwei")
                    .tags("chain", "ethereum", "urgency", "SLOW")
                    .gauge()
                    .value();
            var fastValue = registry.get("str.gas.estimate.gwei")
                    .tags("chain", "ethereum", "urgency", "FAST")
                    .gauge()
                    .value();
            assertThat(slowValue).isEqualTo(20.0);
            assertThat(fastValue).isEqualTo(50.0);
        }
    }
}
