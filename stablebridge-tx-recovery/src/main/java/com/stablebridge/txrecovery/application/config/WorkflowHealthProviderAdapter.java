package com.stablebridge.txrecovery.application.config;

import java.util.Optional;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;
import org.springframework.stereotype.Component;

import com.stablebridge.txrecovery.domain.status.port.WorkflowHealthProvider;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
class WorkflowHealthProviderAdapter implements WorkflowHealthProvider {

    private final Optional<TemporalHealthIndicator> temporalHealthIndicator;

    @Override
    public boolean isHealthy() {
        return temporalHealthIndicator
                .map(indicator -> indicator.health())
                .map(Health::getStatus)
                .map(status -> status.equals(Status.UP))
                .orElse(false);
    }
}
