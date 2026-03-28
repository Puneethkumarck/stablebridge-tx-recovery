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
            // when
            var properties = new TemporalProperties(
                    null, null, null, null, null, null, null);

            // then
            var expected = new TemporalProperties(
                    "localhost:7233",
                    "stablebridge-tx-recovery",
                    "str-transaction-lifecycle",
                    Duration.ofHours(24),
                    Duration.ofHours(2),
                    new TemporalProperties.RpcActivityProperties(
                            Duration.ofSeconds(30), 3, Duration.ofSeconds(1), 2.0),
                    new TemporalProperties.SigningActivityProperties(
                            Duration.ofSeconds(10), 2, Duration.ofSeconds(1), 2.0));

            assertThat(properties)
                    .usingRecursiveComparison()
                    .isEqualTo(expected);
        }

        @Test
        void shouldApplyDefaultRpcActivityValues() {
            // when
            var rpc = new TemporalProperties.RpcActivityProperties(null, null, null, null);

            // then
            var expected = new TemporalProperties.RpcActivityProperties(
                    Duration.ofSeconds(30), 3, Duration.ofSeconds(1), 2.0);

            assertThat(rpc)
                    .usingRecursiveComparison()
                    .isEqualTo(expected);
        }

        @Test
        void shouldApplyDefaultSigningActivityValues() {
            // when
            var signing = new TemporalProperties.SigningActivityProperties(null, null, null, null);

            // then
            var expected = new TemporalProperties.SigningActivityProperties(
                    Duration.ofSeconds(10), 2, Duration.ofSeconds(1), 2.0);

            assertThat(signing)
                    .usingRecursiveComparison()
                    .isEqualTo(expected);
        }
    }

    @Nested
    class CustomValues {

        @Test
        void shouldApplyCustomValues() {
            // when
            var properties = new TemporalProperties(
                    "temporal.prod:7233",
                    "custom-namespace",
                    "custom-queue",
                    Duration.ofHours(48),
                    Duration.ofHours(4),
                    new TemporalProperties.RpcActivityProperties(
                            Duration.ofSeconds(60), 5, Duration.ofSeconds(2), 3.0),
                    new TemporalProperties.SigningActivityProperties(
                            Duration.ofSeconds(20), 4, Duration.ofSeconds(3), 4.0));

            // then
            var expected = new TemporalProperties(
                    "temporal.prod:7233",
                    "custom-namespace",
                    "custom-queue",
                    Duration.ofHours(48),
                    Duration.ofHours(4),
                    new TemporalProperties.RpcActivityProperties(
                            Duration.ofSeconds(60), 5, Duration.ofSeconds(2), 3.0),
                    new TemporalProperties.SigningActivityProperties(
                            Duration.ofSeconds(20), 4, Duration.ofSeconds(3), 4.0));

            assertThat(properties)
                    .usingRecursiveComparison()
                    .isEqualTo(expected);
        }
    }
}
