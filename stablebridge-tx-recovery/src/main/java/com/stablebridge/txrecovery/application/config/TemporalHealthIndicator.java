package com.stablebridge.txrecovery.application.config;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

import io.temporal.serviceclient.WorkflowServiceStubs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class TemporalHealthIndicator implements HealthIndicator {

    private final WorkflowServiceStubs workflowServiceStubs;
    private final TemporalProperties temporalProperties;

    @Override
    public Health health() {
        try {
            workflowServiceStubs.healthCheck();
            return Health.up()
                    .withDetail("target", temporalProperties.target())
                    .withDetail("namespace", temporalProperties.namespace())
                    .withDetail("taskQueue", temporalProperties.taskQueue())
                    .build();
        } catch (Exception e) {
            log.warn("Temporal health check failed", e);
            return Health.down()
                    .withDetail("target", temporalProperties.target())
                    .withException(e)
                    .build();
        }
    }
}
