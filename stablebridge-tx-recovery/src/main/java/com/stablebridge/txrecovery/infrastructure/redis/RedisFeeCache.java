package com.stablebridge.txrecovery.infrastructure.redis;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.springframework.data.redis.core.StringRedisTemplate;

import com.stablebridge.txrecovery.domain.recovery.model.FeeEstimate;
import com.stablebridge.txrecovery.domain.recovery.model.FeeUrgency;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@RequiredArgsConstructor
public class RedisFeeCache {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public Optional<FeeEstimate> read(String chain, FeeUrgency urgency) {
        try {
            var key = RedisKeyNamespace.gasCacheHash(chain);
            var raw = redisTemplate.opsForHash().get(key, urgency.name());
            return Optional.ofNullable(raw)
                    .map(Object::toString)
                    .filter(json -> !"null".equals(json))
                    .map(json -> objectMapper.readValue(json, FeeEstimate.class));
        } catch (Exception e) {
            log.warn("Failed to read fee estimate from cache for chain={} urgency={}", chain, urgency, e);
            return Optional.empty();
        }
    }

    public void write(String chain, FeeUrgency urgency, FeeEstimate estimate, long ttlMillis) {
        try {
            var key = RedisKeyNamespace.gasCacheHash(chain);
            var json = objectMapper.writeValueAsString(estimate);
            redisTemplate.opsForHash().put(key, urgency.name(), json);
            redisTemplate.expire(key, ttlMillis, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            log.warn("Failed to write fee estimate to cache for chain={} urgency={}", chain, urgency, e);
        }
    }
}
