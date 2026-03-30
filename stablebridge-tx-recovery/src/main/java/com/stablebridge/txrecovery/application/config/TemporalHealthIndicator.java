package com.stablebridge.txrecovery.application.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.health.contributor.Status;
import org.springframework.stereotype.Component;

import io.temporal.serviceclient.WorkflowServiceStubs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnBean(WorkflowServiceStubs.class)
public class TemporalHealthIndicator implements HealthIndicator {

    private final WorkflowServiceStubs workflowServiceStubs;
    private final StrProperties strProperties;

    @Override
    public Health health() {
        var temporal = strProperties.temporal();
        try {
            workflowServiceStubs.healthCheck();
            return Health.up()
                    .withDetail("target", temporal.target())
                    .withDetail("namespace", temporal.namespace())
                    .withDetail("taskQueue", temporal.taskQueue())
                    .build();
        } catch (Exception e) {
            log.warn("Temporal health check failed", e);
            return Health.status(new Status("DEGRADED"))
                    .withDetail("target", temporal.target())
                    .withException(e)
                    .build();
        }
    }
}
