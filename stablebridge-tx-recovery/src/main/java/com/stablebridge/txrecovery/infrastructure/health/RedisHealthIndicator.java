package com.stablebridge.txrecovery.infrastructure.health;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnBean(StringRedisTemplate.class)
public class RedisHealthIndicator implements HealthIndicator {

    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public Health health() {
        try {
            var pong = stringRedisTemplate.execute(RedisConnection::ping);
            return Health.up()
                    .withDetail("ping", pong)
                    .build();
        } catch (Exception e) {
            log.warn("Redis health check failed", e);
            return Health.down()
                    .withException(e)
                    .build();
        }
    }
}
