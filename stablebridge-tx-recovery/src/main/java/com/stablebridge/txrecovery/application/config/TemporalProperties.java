package com.stablebridge.txrecovery.application.config;

import java.time.Duration;
import java.util.Objects;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Builder;

@ConfigurationProperties(prefix = "str.temporal")
@Builder(toBuilder = true)
public record TemporalProperties(
        String target,
        String namespace,
        String taskQueue,
        Duration workflowExecutionTimeout,
        Duration workflowRunTimeout,
        RpcActivityProperties rpcActivity,
        SigningActivityProperties signingActivity) {

    public TemporalProperties {
        namespace = Objects.requireNonNullElse(namespace, "stablebridge-tx-recovery");
        taskQueue = Objects.requireNonNullElse(taskQueue, "str-transaction-lifecycle");
        workflowExecutionTimeout = Objects.requireNonNullElse(workflowExecutionTimeout, Duration.ofHours(24));
        workflowRunTimeout = Objects.requireNonNullElse(workflowRunTimeout, Duration.ofHours(2));
        rpcActivity = Objects.requireNonNullElse(rpcActivity, new RpcActivityProperties(null, null, null, null));
        signingActivity = Objects.requireNonNullElse(signingActivity, new SigningActivityProperties(null, null, null, null));
    }

    @Builder(toBuilder = true)
    public record RpcActivityProperties(
            Duration startToCloseTimeout,
            Integer maxAttempts,
            Duration initialInterval,
            Double backoffCoefficient) {

        public RpcActivityProperties {
            startToCloseTimeout = Objects.requireNonNullElse(startToCloseTimeout, Duration.ofSeconds(30));
            maxAttempts = Objects.requireNonNullElse(maxAttempts, 3);
            initialInterval = Objects.requireNonNullElse(initialInterval, Duration.ofSeconds(1));
            backoffCoefficient = Objects.requireNonNullElse(backoffCoefficient, 2.0);
        }
    }

    @Builder(toBuilder = true)
    public record SigningActivityProperties(
            Duration startToCloseTimeout,
            Integer maxAttempts,
            Duration initialInterval,
            Double backoffCoefficient) {

        public SigningActivityProperties {
            startToCloseTimeout = Objects.requireNonNullElse(startToCloseTimeout, Duration.ofSeconds(10));
            maxAttempts = Objects.requireNonNullElse(maxAttempts, 2);
            initialInterval = Objects.requireNonNullElse(initialInterval, Duration.ofSeconds(1));
            backoffCoefficient = Objects.requireNonNullElse(backoffCoefficient, 2.0);
        }
    }
}
