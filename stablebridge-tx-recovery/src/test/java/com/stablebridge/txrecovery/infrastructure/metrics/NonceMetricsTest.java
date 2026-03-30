package com.stablebridge.txrecovery.infrastructure.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class NonceMetricsTest {

    private MeterRegistry registry;
    private NonceMetrics nonceMetrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        nonceMetrics = new NonceMetrics(registry);
    }

    @Nested
    class NonceAllocation {

        @Test
        void shouldRecordNonceAllocated() {
            // given
            var chain = "ethereum";

            // when
            nonceMetrics.recordNonceAllocated(chain);
            nonceMetrics.recordNonceAllocated(chain);

            // then
            var count = registry.get("str.nonce.allocated.total")
                    .tags("chain", "ethereum")
                    .counter()
                    .count();
            assertThat(count).isEqualTo(2.0);
        }

        @Test
        void shouldRecordNonceGapDetected() {
            // given
            var chain = "polygon";

            // when
            nonceMetrics.recordNonceGapDetected(chain);

            // then
            var count = registry.get("str.nonce.gaps.detected.total")
                    .tags("chain", "polygon")
                    .counter()
                    .count();
            assertThat(count).isEqualTo(1.0);
        }
    }

    @Nested
    class NonceInFlight {

        @Test
        void shouldSetNonceInFlight() {
            // given
            var chain = "ethereum";
            var address = "0xabc123";

            // when
            nonceMetrics.setNonceInFlight(chain, address, 5);

            // then
            var value = registry.get("str.nonce.in.flight")
                    .tags("chain", "ethereum", "address", "0xabc123")
                    .gauge()
                    .value();
            assertThat(value).isEqualTo(5.0);
        }

        @Test
        void shouldUpdateNonceInFlight() {
            // given
            var chain = "ethereum";
            var address = "0xabc123";
            nonceMetrics.setNonceInFlight(chain, address, 5);

            // when
            nonceMetrics.setNonceInFlight(chain, address, 3);

            // then
            var value = registry.get("str.nonce.in.flight")
                    .tags("chain", "ethereum", "address", "0xabc123")
                    .gauge()
                    .value();
            assertThat(value).isEqualTo(3.0);
        }
    }
}
