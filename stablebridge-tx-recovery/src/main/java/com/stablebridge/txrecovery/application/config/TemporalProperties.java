package com.stablebridge.txrecovery.application.config;

import java.time.Duration;
import java.util.List;
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
        ActivityOptionsConfig activityOptions,
        List<String> nonRetryableExceptions,
        String workerPackages) {

    public TemporalProperties {
        target = Objects.requireNonNullElse(target, "localhost:7233");
        namespace = Objects.requireNonNullElse(namespace, "stablebridge-tx-recovery");
        taskQueue = Objects.requireNonNullElse(taskQueue, "str-transaction-lifecycle");
        workflowExecutionTimeout = Objects.requireNonNullElse(workflowExecutionTimeout, Duration.ofHours(24));
        workflowRunTimeout = Objects.requireNonNullElse(workflowRunTimeout, Duration.ofHours(2));
        activityOptions = Objects.requireNonNullElse(activityOptions, ActivityOptionsConfig.builder().build());
        nonRetryableExceptions = Objects.requireNonNullElse(nonRetryableExceptions, List.of(
                "com.stablebridge.txrecovery.domain.exception.NonRetryableException",
                "com.stablebridge.txrecovery.domain.exception.NonceTooLowException"));
        workerPackages = Objects.requireNonNullElse(workerPackages, "com.stablebridge.txrecovery");
    }

    @Builder(toBuilder = true)
    public record ActivityOptionsConfig(
            ActivityConfig defaultOptions,
            ActivityConfig signing,
            ActivityConfig confirmation,
            ActivityConfig recoveryExecution) {

        public ActivityOptionsConfig {
            defaultOptions = Objects.requireNonNullElse(defaultOptions, ActivityConfig.builder()
                    .startToCloseTimeout(Duration.ofSeconds(30))
                    .maxAttempts(3)
                    .initialInterval(Duration.ofSeconds(1))
                    .backoffCoefficient(2.0)
                    .build());
            signing = Objects.requireNonNullElse(signing, ActivityConfig.builder()
                    .startToCloseTimeout(Duration.ofSeconds(10))
                    .maxAttempts(2)
                    .initialInterval(Duration.ofSeconds(1))
                    .backoffCoefficient(2.0)
                    .build());
            confirmation = Objects.requireNonNullElse(confirmation, ActivityConfig.builder()
                    .startToCloseTimeout(Duration.ofMinutes(5))
                    .maxAttempts(1)
                    .initialInterval(Duration.ofSeconds(1))
                    .backoffCoefficient(2.0)
                    .build());
            recoveryExecution = Objects.requireNonNullElse(recoveryExecution, ActivityConfig.builder()
                    .startToCloseTimeout(Duration.ofSeconds(60))
                    .maxAttempts(3)
                    .initialInterval(Duration.ofSeconds(1))
                    .backoffCoefficient(2.0)
                    .build());
        }
    }

    @Builder(toBuilder = true)
    public record ActivityConfig(
            Duration startToCloseTimeout,
            Integer maxAttempts,
            Duration initialInterval,
            Double backoffCoefficient) {

        public ActivityConfig {
            startToCloseTimeout = Objects.requireNonNullElse(startToCloseTimeout, Duration.ofSeconds(30));
            maxAttempts = Objects.requireNonNullElse(maxAttempts, 3);
            initialInterval = Objects.requireNonNullElse(initialInterval, Duration.ofSeconds(1));
            backoffCoefficient = Objects.requireNonNullElse(backoffCoefficient, 2.0);
        }
    }
}
