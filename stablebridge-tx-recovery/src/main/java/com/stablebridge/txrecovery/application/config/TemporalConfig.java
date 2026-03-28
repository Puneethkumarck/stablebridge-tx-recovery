package com.stablebridge.txrecovery.application.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.stablebridge.txrecovery.application.workflow.TransactionLifecycleActivities;
import com.stablebridge.txrecovery.application.workflow.TransactionLifecycleWorkflowImpl;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowClientOptions;
import io.temporal.client.WorkflowOptions;
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
    Worker worker(
            WorkerFactory workerFactory,
            TemporalProperties properties,
            ObjectProvider<TransactionLifecycleActivities> activitiesProvider) {
        log.info("Creating Temporal worker for task queue {}", properties.taskQueue());
        var worker = workerFactory.newWorker(properties.taskQueue());
        worker.registerWorkflowImplementationTypes(TransactionLifecycleWorkflowImpl.class);
        activitiesProvider.ifAvailable(worker::registerActivitiesImplementations);
        return worker;
    }

    @Bean
    WorkflowOptions workflowOptions(TemporalProperties properties) {
        return WorkflowOptions.newBuilder()
                .setTaskQueue(properties.taskQueue())
                .setWorkflowExecutionTimeout(properties.workflowExecutionTimeout())
                .setWorkflowRunTimeout(properties.workflowRunTimeout())
                .build();
    }
}
