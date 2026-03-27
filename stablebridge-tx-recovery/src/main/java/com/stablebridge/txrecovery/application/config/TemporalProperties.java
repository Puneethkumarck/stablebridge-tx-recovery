package com.stablebridge.txrecovery.application.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "str.temporal")
public record TemporalProperties(
        String target,
        String namespace,
        String taskQueue,
        Duration workflowExecutionTimeout,
        Duration workflowRunTimeout,
        RpcActivityProperties rpcActivity,
        SigningActivityProperties signingActivity) {

    public TemporalProperties {
        if (namespace == null) {
            namespace = "stablebridge-tx-recovery";
        }
        if (taskQueue == null) {
            taskQueue = "str-transaction-lifecycle";
        }
        if (workflowExecutionTimeout == null) {
            workflowExecutionTimeout = Duration.ofHours(24);
        }
        if (workflowRunTimeout == null) {
            workflowRunTimeout = Duration.ofHours(2);
        }
        if (rpcActivity == null) {
            rpcActivity = new RpcActivityProperties(null, null, null, null);
        }
        if (signingActivity == null) {
            signingActivity = new SigningActivityProperties(null, null);
        }
    }

    public record RpcActivityProperties(
            Duration startToCloseTimeout,
            Integer maxAttempts,
            Duration initialInterval,
            Double backoffCoefficient) {

        public RpcActivityProperties {
            if (startToCloseTimeout == null) {
                startToCloseTimeout = Duration.ofSeconds(30);
            }
            if (maxAttempts == null) {
                maxAttempts = 3;
            }
            if (initialInterval == null) {
                initialInterval = Duration.ofSeconds(1);
            }
            if (backoffCoefficient == null) {
                backoffCoefficient = 2.0;
            }
        }
    }

    public record SigningActivityProperties(
            Duration startToCloseTimeout,
            Integer maxAttempts) {

        public SigningActivityProperties {
            if (startToCloseTimeout == null) {
                startToCloseTimeout = Duration.ofSeconds(10);
            }
            if (maxAttempts == null) {
                maxAttempts = 2;
            }
        }
    }
}
