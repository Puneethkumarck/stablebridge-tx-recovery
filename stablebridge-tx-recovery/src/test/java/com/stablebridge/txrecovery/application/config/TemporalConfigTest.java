package com.stablebridge.txrecovery.application.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import com.stablebridge.txrecovery.domain.exception.NonRetryableException;
import com.stablebridge.txrecovery.domain.exception.NonceTooLowException;

import io.temporal.activity.ActivityOptions;

class TemporalConfigTest {

    private final TemporalConfig config = new TemporalConfig();

    @Test
    void shouldCreateRpcActivityOptionsWithRetryPolicy() {
        // given
        var properties = new TemporalProperties(
                "localhost:7233", null, null, null, null, null, null);

        // when
        var options = config.rpcActivityOptions(properties);

        // then
        assertThat(options.getStartToCloseTimeout()).isEqualTo(Duration.ofSeconds(30));
        assertThat(options.getRetryOptions()).isNotNull();
        assertThat(options.getRetryOptions().getMaximumAttempts()).isEqualTo(3);
        assertThat(options.getRetryOptions().getInitialInterval()).isEqualTo(Duration.ofSeconds(1));
        assertThat(options.getRetryOptions().getBackoffCoefficient()).isEqualTo(2.0);
        assertThat(options.getRetryOptions().getDoNotRetry())
                .containsExactlyInAnyOrder(
                        NonRetryableException.class.getName(),
                        NonceTooLowException.class.getName());
    }

    @Test
    void shouldCreateSigningActivityOptionsWithRetryPolicy() {
        // given
        var properties = new TemporalProperties(
                "localhost:7233", null, null, null, null, null, null);

        // when
        var options = config.signingActivityOptions(properties);

        // then
        assertThat(options.getStartToCloseTimeout()).isEqualTo(Duration.ofSeconds(10));
        assertThat(options.getRetryOptions()).isNotNull();
        assertThat(options.getRetryOptions().getMaximumAttempts()).isEqualTo(2);
        assertThat(options.getRetryOptions().getDoNotRetry())
                .containsExactlyInAnyOrder(
                        NonRetryableException.class.getName(),
                        NonceTooLowException.class.getName());
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
        var options = config.rpcActivityOptions(properties);

        // then
        assertThat(options.getStartToCloseTimeout()).isEqualTo(Duration.ofSeconds(60));
        assertThat(options.getRetryOptions().getMaximumAttempts()).isEqualTo(5);
        assertThat(options.getRetryOptions().getInitialInterval()).isEqualTo(Duration.ofSeconds(2));
        assertThat(options.getRetryOptions().getBackoffCoefficient()).isEqualTo(3.0);
    }

    @Test
    void shouldCreateSigningActivityOptionsWithCustomProperties() {
        // given
        var properties = new TemporalProperties(
                "localhost:7233", null, null, null, null, null,
                new TemporalProperties.SigningActivityProperties(
                        Duration.ofSeconds(20), 4));

        // when
        var options = config.signingActivityOptions(properties);

        // then
        assertThat(options.getStartToCloseTimeout()).isEqualTo(Duration.ofSeconds(20));
        assertThat(options.getRetryOptions().getMaximumAttempts()).isEqualTo(4);
    }

    @Test
    void shouldReturnActivityOptionsType() {
        // given
        var properties = new TemporalProperties(
                "localhost:7233", null, null, null, null, null, null);

        // when
        var rpcOptions = config.rpcActivityOptions(properties);
        var signingOptions = config.signingActivityOptions(properties);

        // then
        assertThat(rpcOptions).isInstanceOf(ActivityOptions.class);
        assertThat(signingOptions).isInstanceOf(ActivityOptions.class);
    }
}
