package com.stablebridge.txrecovery.application.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.stablebridge.txrecovery.domain.exception.NonRetryableException;
import com.stablebridge.txrecovery.domain.exception.NonceTooLowException;

import io.temporal.activity.ActivityOptions;
import io.temporal.client.WorkflowOptions;
import io.temporal.common.RetryOptions;

class TemporalConfigTest {

    private final TemporalConfig config = new TemporalConfig();

    @Nested
    class RpcActivityOptionsBean {

        @Test
        void shouldCreateRpcActivityOptionsWithDefaultRetryPolicy() {
            // given
            var properties = new TemporalProperties(
                    "localhost:7233", null, null, null, null, null, null);

            // when
            var result = config.rpcActivityOptions(properties);

            // then
            var expected = ActivityOptions.newBuilder()
                    .setStartToCloseTimeout(Duration.ofSeconds(30))
                    .setRetryOptions(RetryOptions.newBuilder()
                            .setMaximumAttempts(3)
                            .setInitialInterval(Duration.ofSeconds(1))
                            .setBackoffCoefficient(2.0)
                            .setDoNotRetry(
                                    NonRetryableException.class.getName(),
                                    NonceTooLowException.class.getName())
                            .build())
                    .build();

            assertThat(result)
                    .usingRecursiveComparison()
                    .isEqualTo(expected);
        }

        @Test
        void shouldCreateRpcActivityOptionsWithCustomProperties() {
            // given
            var properties = new TemporalProperties(
                    "localhost:7233", null, null, null, null,
                    new TemporalProperties.RpcActivityProperties(
                            Duration.ofSeconds(60), 5, Duration.ofSeconds(2), 3.0),
                    null);

            // when
            var result = config.rpcActivityOptions(properties);

            // then
            var expected = ActivityOptions.newBuilder()
                    .setStartToCloseTimeout(Duration.ofSeconds(60))
                    .setRetryOptions(RetryOptions.newBuilder()
                            .setMaximumAttempts(5)
                            .setInitialInterval(Duration.ofSeconds(2))
                            .setBackoffCoefficient(3.0)
                            .setDoNotRetry(
                                    NonRetryableException.class.getName(),
                                    NonceTooLowException.class.getName())
                            .build())
                    .build();

            assertThat(result)
                    .usingRecursiveComparison()
                    .isEqualTo(expected);
        }
    }

    @Nested
    class SigningActivityOptionsBean {

        @Test
        void shouldCreateSigningActivityOptionsWithDefaultRetryPolicy() {
            // given
            var properties = new TemporalProperties(
                    "localhost:7233", null, null, null, null, null, null);

            // when
            var result = config.signingActivityOptions(properties);

            // then
            var expected = ActivityOptions.newBuilder()
                    .setStartToCloseTimeout(Duration.ofSeconds(10))
                    .setRetryOptions(RetryOptions.newBuilder()
                            .setMaximumAttempts(2)
                            .setInitialInterval(Duration.ofSeconds(1))
                            .setBackoffCoefficient(2.0)
                            .setDoNotRetry(
                                    NonRetryableException.class.getName(),
                                    NonceTooLowException.class.getName())
                            .build())
                    .build();

            assertThat(result)
                    .usingRecursiveComparison()
                    .isEqualTo(expected);
        }

        @Test
        void shouldCreateSigningActivityOptionsWithCustomProperties() {
            // given
            var properties = new TemporalProperties(
                    "localhost:7233", null, null, null, null, null,
                    new TemporalProperties.SigningActivityProperties(
                            Duration.ofSeconds(20), 4, Duration.ofSeconds(3), 4.0));

            // when
            var result = config.signingActivityOptions(properties);

            // then
            var expected = ActivityOptions.newBuilder()
                    .setStartToCloseTimeout(Duration.ofSeconds(20))
                    .setRetryOptions(RetryOptions.newBuilder()
                            .setMaximumAttempts(4)
                            .setInitialInterval(Duration.ofSeconds(3))
                            .setBackoffCoefficient(4.0)
                            .setDoNotRetry(
                                    NonRetryableException.class.getName(),
                                    NonceTooLowException.class.getName())
                            .build())
                    .build();

            assertThat(result)
                    .usingRecursiveComparison()
                    .isEqualTo(expected);
        }
    }

    @Nested
    class WorkflowOptionsBean {

        @Test
        void shouldCreateWorkflowOptionsWithTimeouts() {
            // given
            var properties = new TemporalProperties(
                    "localhost:7233", null, null, null, null, null, null);

            // when
            var result = config.workflowOptions(properties);

            // then
            var expected = WorkflowOptions.newBuilder()
                    .setTaskQueue("str-transaction-lifecycle")
                    .setWorkflowExecutionTimeout(Duration.ofHours(24))
                    .setWorkflowRunTimeout(Duration.ofHours(2))
                    .build();

            assertThat(result)
                    .usingRecursiveComparison()
                    .isEqualTo(expected);
        }

        @Test
        void shouldCreateWorkflowOptionsWithCustomTimeouts() {
            // given
            var properties = new TemporalProperties(
                    "localhost:7233", null, "custom-queue",
                    Duration.ofHours(48), Duration.ofHours(4), null, null);

            // when
            var result = config.workflowOptions(properties);

            // then
            var expected = WorkflowOptions.newBuilder()
                    .setTaskQueue("custom-queue")
                    .setWorkflowExecutionTimeout(Duration.ofHours(48))
                    .setWorkflowRunTimeout(Duration.ofHours(4))
                    .build();

            assertThat(result)
                    .usingRecursiveComparison()
                    .isEqualTo(expected);
        }
    }
}
