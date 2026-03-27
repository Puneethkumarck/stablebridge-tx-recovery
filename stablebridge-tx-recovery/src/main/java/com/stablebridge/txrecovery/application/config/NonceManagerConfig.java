package com.stablebridge.txrecovery.application.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.stablebridge.txrecovery.domain.address.port.NonceManager;
import com.stablebridge.txrecovery.infrastructure.client.evm.EvmRpcClient;
import com.stablebridge.txrecovery.infrastructure.redis.NonceManagerProperties;
import com.stablebridge.txrecovery.infrastructure.redis.RedisNonceManager;

import io.micrometer.core.instrument.MeterRegistry;

@Configuration
@EnableConfigurationProperties(NonceManagerProperties.class)
@ConditionalOnBean(StringRedisTemplate.class)
public class NonceManagerConfig {

    @Bean
    NonceManager nonceManager(
            StringRedisTemplate stringRedisTemplate,
            EvmRpcClient evmRpcClient,
            MeterRegistry meterRegistry) {
        return new RedisNonceManager(stringRedisTemplate, evmRpcClient, meterRegistry);
    }
}
