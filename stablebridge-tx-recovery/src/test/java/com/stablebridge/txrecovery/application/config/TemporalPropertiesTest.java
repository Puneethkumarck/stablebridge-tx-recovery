package com.stablebridge.txrecovery.application.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class TemporalPropertiesTest {

    @Nested
    class DefaultValues {

        @Test
        void shouldApplyDefaultValues() {
            var properties = new TemporalProperties(null, null, null, null, null);

            var expected = new TemporalProperties(
                    "localhost:7233",
                    "stablebridge-tx-recovery",
                    "str-transaction-lifecycle",
                    Duration.ofHours(24),
                    Duration.ofHours(2));

            assertThat(properties)
                    .usingRecursiveComparison()
                    .isEqualTo(expected);
        }
    }

    @Nested
    class CustomValues {

        @Test
        void shouldApplyCustomValues() {
            var properties = new TemporalProperties(
                    "temporal.prod:7233",
                    "custom-namespace",
                    "custom-queue",
                    Duration.ofHours(48),
                    Duration.ofHours(4));

            var expected = new TemporalProperties(
                    "temporal.prod:7233",
                    "custom-namespace",
                    "custom-queue",
                    Duration.ofHours(48),
                    Duration.ofHours(4));

            assertThat(properties)
                    .usingRecursiveComparison()
                    .isEqualTo(expected);
        }
    }
}
