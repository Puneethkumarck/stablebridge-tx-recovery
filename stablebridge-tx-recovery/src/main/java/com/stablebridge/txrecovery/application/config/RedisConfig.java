package com.stablebridge.txrecovery.application.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import lombok.extern.slf4j.Slf4j;

@Configuration
@Slf4j
public class RedisConfig {

    public static final String NONCE_KEY_PREFIX = "str:nonce:";
    public static final String NONCE_INFLIGHT_KEY_PREFIX = "str:nonce:inflight:";
    public static final String POOL_LOCK_KEY_PREFIX = "str:pool:lock:";
    public static final String GAS_CACHE_KEY_PREFIX = "str:gas:cache:";

    @Bean
    @ConditionalOnBean(RedisConnectionFactory.class)
    StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        log.info("Configuring StringRedisTemplate with connection factory: {}", connectionFactory.getClass().getSimpleName());
        return new StringRedisTemplate(connectionFactory);
    }
}
