package com.stablebridge.txrecovery.infrastructure.redis;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.stablebridge.txrecovery.domain.recovery.port.FeeCache;

import tools.jackson.databind.ObjectMapper;

@Configuration
class RedisCacheConfig {

    @Bean
    @ConditionalOnBean(StringRedisTemplate.class)
    @ConditionalOnMissingBean(FeeCache.class)
    FeeCache redisFeeCache(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        return new RedisFeeCache(redisTemplate, objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean(FeeCache.class)
    FeeCache noOpFeeCache() {
        return new NoOpFeeCache();
    }
}
