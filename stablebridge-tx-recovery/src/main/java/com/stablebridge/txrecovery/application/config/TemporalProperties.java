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
        Duration workflowRunTimeout) {

    public TemporalProperties {
        target = Objects.requireNonNullElse(target, "localhost:7233");
        namespace = Objects.requireNonNullElse(namespace, "stablebridge-tx-recovery");
        taskQueue = Objects.requireNonNullElse(taskQueue, "str-transaction-lifecycle");
        workflowExecutionTimeout = Objects.requireNonNullElse(workflowExecutionTimeout, Duration.ofHours(24));
        workflowRunTimeout = Objects.requireNonNullElse(workflowRunTimeout, Duration.ofHours(2));
    }
}
