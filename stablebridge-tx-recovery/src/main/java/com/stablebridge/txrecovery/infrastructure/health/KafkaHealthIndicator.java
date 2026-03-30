package com.stablebridge.txrecovery.infrastructure.health;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnBean(KafkaAdmin.class)
public class KafkaHealthIndicator implements HealthIndicator {

    private final KafkaAdmin kafkaAdmin;

    @Override
    public Health health() {
        try {
            var clusterId = kafkaAdmin.clusterId();
            return Health.up()
                    .withDetail("clusterId", clusterId)
                    .build();
        } catch (Exception e) {
            log.warn("Kafka health check failed", e);
            return Health.down()
                    .withException(e)
                    .build();
        }
    }
}
