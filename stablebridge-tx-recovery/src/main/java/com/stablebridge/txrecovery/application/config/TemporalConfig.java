package com.stablebridge.txrecovery.application.config;

import java.time.Duration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.stablebridge.txrecovery.domain.exception.NonRetryableException;
import com.stablebridge.txrecovery.domain.exception.NonceTooLowException;

import io.temporal.activity.ActivityOptions;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowClientOptions;
import io.temporal.common.RetryOptions;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
@EnableConfigurationProperties(TemporalProperties.class)
@ConditionalOnProperty(prefix = "str.temporal", name = "enabled", havingValue = "true", matchIfMissing = true)
public class TemporalConfig {

    @Bean
    WorkflowServiceStubs workflowServiceStubs(TemporalProperties properties) {
        var options = WorkflowServiceStubsOptions.newBuilder()
                .setTarget(properties.target())
                .build();
        log.info("Connecting to Temporal server at {}", properties.target());
        return WorkflowServiceStubs.newServiceStubs(options);
    }

    @Bean
    WorkflowClient workflowClient(
            WorkflowServiceStubs serviceStubs, TemporalProperties properties) {
        var options = WorkflowClientOptions.newBuilder()
                .setNamespace(properties.namespace())
                .build();
        log.info("Creating Temporal WorkflowClient for namespace {}", properties.namespace());
        return WorkflowClient.newInstance(serviceStubs, options);
    }

    @Bean
    WorkerFactory workerFactory(WorkflowClient workflowClient) {
        return WorkerFactory.newInstance(workflowClient);
    }

    @Bean
    Worker worker(WorkerFactory workerFactory, TemporalProperties properties) {
        log.info("Creating Temporal worker for task queue {}", properties.taskQueue());
        return workerFactory.newWorker(properties.taskQueue());
    }

    @Bean
    ActivityOptions rpcActivityOptions(TemporalProperties properties) {
        var rpc = properties.rpcActivity();
        return ActivityOptions.newBuilder()
                .setStartToCloseTimeout(rpc.startToCloseTimeout())
                .setRetryOptions(RetryOptions.newBuilder()
                        .setMaximumAttempts(rpc.maxAttempts())
                        .setInitialInterval(rpc.initialInterval())
                        .setBackoffCoefficient(rpc.backoffCoefficient())
                        .setDoNotRetry(
                                NonRetryableException.class.getName(),
                                NonceTooLowException.class.getName())
                        .build())
                .build();
    }

    @Bean
    ActivityOptions signingActivityOptions(TemporalProperties properties) {
        var signing = properties.signingActivity();
        return ActivityOptions.newBuilder()
                .setStartToCloseTimeout(signing.startToCloseTimeout())
                .setRetryOptions(RetryOptions.newBuilder()
                        .setMaximumAttempts(signing.maxAttempts())
                        .setInitialInterval(Duration.ofSeconds(1))
                        .setBackoffCoefficient(2.0)
                        .setDoNotRetry(
                                NonRetryableException.class.getName(),
                                NonceTooLowException.class.getName())
                        .build())
                .build();
    }
}
