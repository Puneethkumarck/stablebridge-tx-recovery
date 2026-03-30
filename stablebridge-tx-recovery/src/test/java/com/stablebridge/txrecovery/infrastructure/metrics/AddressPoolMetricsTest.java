package com.stablebridge.txrecovery.infrastructure.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class AddressPoolMetricsTest {

    private MeterRegistry registry;
    private AddressPoolMetrics addressPoolMetrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        addressPoolMetrics = new AddressPoolMetrics(registry);
    }

    @Nested
    class PoolSize {

        @Test
        void shouldSetPoolSize() {
            // given
            var chain = "ethereum";
            var tier = "HOT";
            var status = "ACTIVE";

            // when
            addressPoolMetrics.setPoolSize(chain, tier, status, 10);

            // then
            var gauge = registry.get("str.address.pool.size")
                    .tags("chain", "ethereum", "tier", "HOT", "status", "ACTIVE")
                    .gauge();
            assertThat(gauge.value()).isEqualTo(10.0);
        }

        @Test
        void shouldUpdatePoolSize() {
            // given
            var chain = "ethereum";
            var tier = "HOT";
            var status = "ACTIVE";

            // when
            addressPoolMetrics.setPoolSize(chain, tier, status, 10);
            addressPoolMetrics.setPoolSize(chain, tier, status, 25);

            // then
            var gauge = registry.get("str.address.pool.size")
                    .tags("chain", "ethereum", "tier", "HOT", "status", "ACTIVE")
                    .gauge();
            assertThat(gauge.value()).isEqualTo(25.0);
        }
    }

    @Nested
    class InFlight {

        @Test
        void shouldSetInFlight() {
            // given
            var chain = "ethereum";
            var address = "0xABC123";

            // when
            addressPoolMetrics.setInFlight(chain, address, 3);

            // then
            var gauge = registry.get("str.address.pool.in.flight")
                    .tags("chain", "ethereum", "address", "0xABC123")
                    .gauge();
            assertThat(gauge.value()).isEqualTo(3.0);
        }

        @Test
        void shouldTrackMultipleAddresses() {
            // given
            var chain = "ethereum";
            var address1 = "0xABC123";
            var address2 = "0xDEF456";

            // when
            addressPoolMetrics.setInFlight(chain, address1, 3);
            addressPoolMetrics.setInFlight(chain, address2, 7);

            // then
            var gauge1 = registry.get("str.address.pool.in.flight")
                    .tags("chain", "ethereum", "address", "0xABC123")
                    .gauge();
            var gauge2 = registry.get("str.address.pool.in.flight")
                    .tags("chain", "ethereum", "address", "0xDEF456")
                    .gauge();
            assertThat(gauge1.value()).isEqualTo(3.0);
            assertThat(gauge2.value()).isEqualTo(7.0);
        }
    }
}
