package com.stablebridge.txrecovery.application.config;

import java.util.Map;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.stablebridge.txrecovery.application.workflow.TransactionLifecycleActivities;
import com.stablebridge.txrecovery.application.workflow.TransactionLifecycleWorkflowImpl;

import io.temporal.activity.ActivityOptions;
import io.temporal.api.enums.v1.WorkflowIdReusePolicy;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowClientOptions;
import io.temporal.client.WorkflowOptions;
import io.temporal.common.RetryOptions;
import io.temporal.common.converter.DataConverter;
import io.temporal.common.converter.DefaultDataConverter;
import io.temporal.common.converter.JacksonJsonPayloadConverter;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;
import io.temporal.worker.WorkflowImplementationOptions;
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
    DataConverter dataConverter() {
        var objectMapper = new ObjectMapper()
                .findAndRegisterModules()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return DefaultDataConverter.newDefaultInstance()
                .withPayloadConverterOverrides(new JacksonJsonPayloadConverter(objectMapper));
    }

    @Bean
    WorkflowClient workflowClient(
            WorkflowServiceStubs serviceStubs,
            TemporalProperties properties,
            DataConverter dataConverter) {
        var options = WorkflowClientOptions.newBuilder()
                .setNamespace(properties.namespace())
                .setDataConverter(dataConverter)
                .build();
        log.info("Creating Temporal WorkflowClient for namespace {}", properties.namespace());
        return WorkflowClient.newInstance(serviceStubs, options);
    }

    @Bean
    WorkerFactory workerFactory(WorkflowClient workflowClient) {
        return WorkerFactory.newInstance(workflowClient);
    }

    @Bean
    WorkflowImplementationOptions workflowImplementationOptions(TemporalProperties properties) {
        var defaults = properties.activityOptions().defaultOptions();
        var nonRetryable = properties.nonRetryableExceptions().toArray(String[]::new);

        var signingConfig = properties.activityOptions().signing();
        var signingOptions = buildActivityOptions(signingConfig, defaults, nonRetryable);

        var confirmationConfig = properties.activityOptions().confirmation();
        var confirmationOptions = buildActivityOptions(confirmationConfig, defaults, nonRetryable);

        var recoveryConfig = properties.activityOptions().recoveryExecution();
        var recoveryOptions = buildActivityOptions(recoveryConfig, defaults, nonRetryable);

        var activityMethodOptions = Map.of(
                "sign", signingOptions,
                "waitForFinality", confirmationOptions,
                "executeRecovery", recoveryOptions);

        return WorkflowImplementationOptions.newBuilder()
                .setDefaultActivityOptions(buildActivityOptions(defaults, defaults, nonRetryable))
                .setActivityOptions(activityMethodOptions)
                .build();
    }

    @Bean
    Worker worker(
            WorkerFactory workerFactory,
            TemporalProperties properties,
            WorkflowImplementationOptions workflowImplOptions,
            ObjectProvider<TransactionLifecycleActivities> activitiesProvider) {
        log.info("Creating Temporal worker for task queue {}", properties.taskQueue());
        var worker = workerFactory.newWorker(properties.taskQueue());
        worker.registerWorkflowImplementationTypes(workflowImplOptions, TransactionLifecycleWorkflowImpl.class);
        activitiesProvider.ifAvailable(worker::registerActivitiesImplementations);
        return worker;
    }

    @Bean
    WorkflowOptions workflowOptions(TemporalProperties properties) {
        return WorkflowOptions.newBuilder()
                .setTaskQueue(properties.taskQueue())
                .setWorkflowExecutionTimeout(properties.workflowExecutionTimeout())
                .setWorkflowRunTimeout(properties.workflowRunTimeout())
                .setWorkflowIdReusePolicy(
                        WorkflowIdReusePolicy.WORKFLOW_ID_REUSE_POLICY_ALLOW_DUPLICATE_FAILED_ONLY)
                .build();
    }

    private ActivityOptions buildActivityOptions(
            TemporalProperties.ActivityConfig config,
            TemporalProperties.ActivityConfig defaults,
            String[] nonRetryable) {
        var retryOptions = RetryOptions.newBuilder()
                .setMaximumAttempts(config.maxAttempts() != null ? config.maxAttempts() : defaults.maxAttempts())
                .setInitialInterval(config.initialInterval() != null ? config.initialInterval() : defaults.initialInterval())
                .setBackoffCoefficient(config.backoffCoefficient() != null ? config.backoffCoefficient() : defaults.backoffCoefficient())
                .setDoNotRetry(nonRetryable)
                .build();

        return ActivityOptions.newBuilder()
                .setStartToCloseTimeout(config.startToCloseTimeout() != null
                        ? config.startToCloseTimeout() : defaults.startToCloseTimeout())
                .setRetryOptions(retryOptions)
                .build();
    }
}
