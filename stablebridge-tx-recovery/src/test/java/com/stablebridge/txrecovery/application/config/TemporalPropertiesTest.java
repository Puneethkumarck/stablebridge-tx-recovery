package com.stablebridge.txrecovery.application.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class TemporalPropertiesTest {

    @Test
    void shouldApplyDefaultValues() {
        // when
        var properties = new TemporalProperties(
                "localhost:7233", null, null, null, null, null, null);

        // then
        assertThat(properties.target()).isEqualTo("localhost:7233");
        assertThat(properties.namespace()).isEqualTo("stablebridge-tx-recovery");
        assertThat(properties.taskQueue()).isEqualTo("str-transaction-lifecycle");
        assertThat(properties.workflowExecutionTimeout()).isEqualTo(Duration.ofHours(24));
        assertThat(properties.workflowRunTimeout()).isEqualTo(Duration.ofHours(2));
    }

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
                        Duration.ofSeconds(20), 4));

        // then
        assertThat(properties.target()).isEqualTo("temporal.prod:7233");
        assertThat(properties.namespace()).isEqualTo("custom-namespace");
        assertThat(properties.taskQueue()).isEqualTo("custom-queue");
        assertThat(properties.workflowExecutionTimeout()).isEqualTo(Duration.ofHours(48));
        assertThat(properties.workflowRunTimeout()).isEqualTo(Duration.ofHours(4));
        assertThat(properties.rpcActivity().startToCloseTimeout()).isEqualTo(Duration.ofSeconds(60));
        assertThat(properties.rpcActivity().maxAttempts()).isEqualTo(5);
        assertThat(properties.rpcActivity().initialInterval()).isEqualTo(Duration.ofSeconds(2));
        assertThat(properties.rpcActivity().backoffCoefficient()).isEqualTo(3.0);
        assertThat(properties.signingActivity().startToCloseTimeout()).isEqualTo(Duration.ofSeconds(20));
        assertThat(properties.signingActivity().maxAttempts()).isEqualTo(4);
    }

    @Test
    void shouldApplyDefaultRpcActivityValues() {
        // when
        var rpc = new TemporalProperties.RpcActivityProperties(null, null, null, null);

        // then
        assertThat(rpc.startToCloseTimeout()).isEqualTo(Duration.ofSeconds(30));
        assertThat(rpc.maxAttempts()).isEqualTo(3);
        assertThat(rpc.initialInterval()).isEqualTo(Duration.ofSeconds(1));
        assertThat(rpc.backoffCoefficient()).isEqualTo(2.0);
    }

    @Test
    void shouldApplyDefaultSigningActivityValues() {
        // when
        var signing = new TemporalProperties.SigningActivityProperties(null, null);

        // then
        assertThat(signing.startToCloseTimeout()).isEqualTo(Duration.ofSeconds(10));
        assertThat(signing.maxAttempts()).isEqualTo(2);
    }
}
