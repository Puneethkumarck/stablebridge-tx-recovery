package com.stablebridge.txrecovery.infrastructure.redis;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import tools.jackson.databind.ObjectMapper;

@Configuration
class RedisCacheConfig {

    @Bean
    @ConditionalOnBean(StringRedisTemplate.class)
    RedisFeeCache redisFeeCache(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        return new RedisFeeCache(redisTemplate, objectMapper);
    }
}
