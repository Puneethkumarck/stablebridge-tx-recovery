package com.stablebridge.txrecovery.application.config;

import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettucePoolingClientConfiguration;
import org.springframework.data.redis.core.StringRedisTemplate;

import io.lettuce.core.api.StatefulConnection;
import lombok.extern.slf4j.Slf4j;

@Configuration
@Slf4j
@ConditionalOnProperty(name = "str.redis.host")
public class RedisConfig {

    static final int MAX_TOTAL = 16;
    static final int MAX_IDLE = 8;
    static final int MIN_IDLE = 2;

    @Bean
    RedisConnectionFactory redisConnectionFactory(
            @Value("${str.redis.host}") String host, @Value("${str.redis.port:6379}") int port) {
        var standaloneConfig = new RedisStandaloneConfiguration(host, port);

        var poolConfig = new GenericObjectPoolConfig<StatefulConnection<?, ?>>();
        poolConfig.setMaxTotal(MAX_TOTAL);
        poolConfig.setMaxIdle(MAX_IDLE);
        poolConfig.setMinIdle(MIN_IDLE);

        var clientConfig =
                LettucePoolingClientConfiguration.builder().poolConfig(poolConfig).build();

        log.info("Configuring Redis connection factory with host: {} port: {}", host, port);
        return new LettuceConnectionFactory(standaloneConfig, clientConfig);
    }

    @Bean
    StringRedisTemplate stringRedisTemplate(RedisConnectionFactory redisConnectionFactory) {
        return new StringRedisTemplate(redisConnectionFactory);
    }
}
