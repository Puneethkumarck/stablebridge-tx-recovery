package com.stablebridge.txrecovery.infrastructure.redis;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.stablebridge.txrecovery.domain.address.port.NonceManager;
import com.stablebridge.txrecovery.infrastructure.client.evm.EvmRpcClient;

import io.micrometer.core.instrument.MeterRegistry;

@Configuration
@EnableConfigurationProperties(NonceManagerProperties.class)
@ConditionalOnProperty(name = "str.nonce.enabled", havingValue = "true")
public class NonceManagerConfig {

    @Bean
    NonceManager nonceManager(
            StringRedisTemplate stringRedisTemplate,
            EvmRpcClient evmRpcClient,
            MeterRegistry meterRegistry) {
        return new RedisNonceManager(stringRedisTemplate, evmRpcClient, meterRegistry);
    }
}
