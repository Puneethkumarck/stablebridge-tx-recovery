package com.stablebridge.txrecovery.application.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class TemporalPropertiesTest {

    @Nested
    class DefaultValues {

        @Test
        void shouldApplyDefaultValues() {
            // given / when
            var properties = new TemporalProperties(
                    null, null, null, null, null, null, null);

            // then
            assertThat(properties.target()).isEqualTo("localhost:7233");
            assertThat(properties.namespace()).isEqualTo("stablebridge-tx-recovery");
            assertThat(properties.taskQueue()).isEqualTo("str-transaction-lifecycle");
            assertThat(properties.workflowExecutionTimeout()).isEqualTo(Duration.ofHours(24));
            assertThat(properties.workflowRunTimeout()).isEqualTo(Duration.ofHours(2));
            assertThat(properties.activityOptions()).isNotNull();
            assertThat(properties.nonRetryableExceptions()).containsExactly(
                    "com.stablebridge.txrecovery.domain.exception.NonRetryableException",
                    "com.stablebridge.txrecovery.domain.exception.NonceTooLowException");
        }
    }

    @Nested
    class CustomValues {

        @Test
        void shouldApplyCustomValues() {
            // given
            var activityOptions = new TemporalProperties.ActivityOptionsConfig(
                    new TemporalProperties.ActivityConfig(
                            Duration.ofSeconds(45), 5, Duration.ofSeconds(2), 3.0),
                    new TemporalProperties.ActivityConfig(
                            Duration.ofSeconds(15), 3, Duration.ofSeconds(2), 2.5),
                    new TemporalProperties.ActivityConfig(
                            Duration.ofSeconds(600), 2, Duration.ofSeconds(5), 3.0),
                    new TemporalProperties.ActivityConfig(
                            Duration.ofSeconds(120), 4, Duration.ofSeconds(3), 2.5));

            var nonRetryable = List.of(
                    "com.stablebridge.txrecovery.domain.exception.CustomException");

            // when
            var properties = new TemporalProperties(
                    "temporal.prod:7233",
                    "custom-namespace",
                    "custom-queue",
                    Duration.ofHours(48),
                    Duration.ofHours(4),
                    activityOptions,
                    nonRetryable);

            // then
            var expected = new TemporalProperties(
                    "temporal.prod:7233",
                    "custom-namespace",
                    "custom-queue",
                    Duration.ofHours(48),
                    Duration.ofHours(4),
                    activityOptions,
                    nonRetryable);

            assertThat(properties)
                    .usingRecursiveComparison()
                    .isEqualTo(expected);
        }
    }

    @Nested
    class ActivityOptionsDefaults {

        @Test
        void shouldApplyDefaultActivityOptionsWhenNull() {
            // given / when
            var properties = new TemporalProperties(
                    null, null, null, null, null, null, null);

            // then
            var expectedActivityOptions = new TemporalProperties.ActivityOptionsConfig(
                    new TemporalProperties.ActivityConfig(
                            Duration.ofSeconds(30), 3, Duration.ofSeconds(1), 2.0),
                    new TemporalProperties.ActivityConfig(
                            Duration.ofSeconds(10), 2, Duration.ofSeconds(1), 2.0),
                    new TemporalProperties.ActivityConfig(
                            Duration.ofSeconds(300), 1, Duration.ofSeconds(1), 2.0),
                    new TemporalProperties.ActivityConfig(
                            Duration.ofSeconds(60), 3, Duration.ofSeconds(1), 2.0));

            assertThat(properties.activityOptions())
                    .usingRecursiveComparison()
                    .isEqualTo(expectedActivityOptions);
        }

        @Test
        void shouldApplyDefaultNonRetryableExceptions() {
            // given / when
            var properties = new TemporalProperties(
                    null, null, null, null, null, null, null);

            // then
            var expectedExceptions = List.of(
                    "com.stablebridge.txrecovery.domain.exception.NonRetryableException",
                    "com.stablebridge.txrecovery.domain.exception.NonceTooLowException");

            assertThat(properties.nonRetryableExceptions())
                    .containsExactlyElementsOf(expectedExceptions);
        }

        @Test
        void shouldPreserveCustomActivityOptionsWhenProvided() {
            // given
            var customDefault = new TemporalProperties.ActivityConfig(
                    Duration.ofSeconds(45), 5, Duration.ofSeconds(2), 3.0);
            var customSigning = new TemporalProperties.ActivityConfig(
                    Duration.ofSeconds(15), 3, Duration.ofSeconds(2), 2.5);
            var customConfirmation = new TemporalProperties.ActivityConfig(
                    Duration.ofSeconds(600), 2, Duration.ofSeconds(5), 3.0);
            var customRecovery = new TemporalProperties.ActivityConfig(
                    Duration.ofSeconds(120), 4, Duration.ofSeconds(3), 2.5);
            var customOptions = new TemporalProperties.ActivityOptionsConfig(
                    customDefault, customSigning, customConfirmation, customRecovery);

            // when
            var properties = new TemporalProperties(
                    null, null, null, null, null, customOptions, null);

            // then
            assertThat(properties.activityOptions())
                    .usingRecursiveComparison()
                    .isEqualTo(customOptions);
        }

        @Test
        void shouldApplyDefaultActivityConfigValuesWhenFieldsAreNull() {
            // given
            var partialConfig = new TemporalProperties.ActivityConfig(null, null, null, null);
            var partialOptions = new TemporalProperties.ActivityOptionsConfig(
                    partialConfig, null, null, null);

            // when
            var properties = new TemporalProperties(
                    null, null, null, null, null, partialOptions, null);

            // then
            var expectedDefault = new TemporalProperties.ActivityConfig(
                    Duration.ofSeconds(30), 3, Duration.ofSeconds(1), 2.0);
            assertThat(properties.activityOptions().defaultOptions())
                    .usingRecursiveComparison()
                    .isEqualTo(expectedDefault);
        }
    }
}
