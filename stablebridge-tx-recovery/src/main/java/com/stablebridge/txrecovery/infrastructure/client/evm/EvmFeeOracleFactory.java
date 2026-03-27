package com.stablebridge.txrecovery.infrastructure.client.evm;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.redis.core.StringRedisTemplate;

import com.stablebridge.txrecovery.domain.recovery.port.FeeOracle;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import tools.jackson.databind.ObjectMapper;

public class EvmFeeOracleFactory {

    private final List<ChainInput> chainInputs;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final RateLimiterRegistry rateLimiterRegistry;

    public EvmFeeOracleFactory(
            List<ChainInput> chainInputs,
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            CircuitBreakerRegistry circuitBreakerRegistry,
            RateLimiterRegistry rateLimiterRegistry) {
        this.chainInputs = chainInputs;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.circuitBreakerRegistry = circuitBreakerRegistry;
        this.rateLimiterRegistry = rateLimiterRegistry;
    }

    public Map<String, FeeOracle> createAll() {
        var oracles = new LinkedHashMap<String, FeeOracle>();
        chainInputs.forEach(input -> {
            var rpcClient = new EvmRpcClient(
                    input.name(),
                    input.rpcUrls(),
                    input.rpcTimeout(),
                    input.rateLimitPerSecond(),
                    input.rateLimitBurst(),
                    circuitBreakerRegistry,
                    rateLimiterRegistry);

            var properties = EvmChainProperties.builder()
                    .chain(input.name())
                    .maxFeeCapGwei(input.maxFeeCapGwei())
                    .blockTime(input.blockTime())
                    .build();

            oracles.put(input.name(), new EvmFeeOracle(rpcClient, properties, redisTemplate, objectMapper));
        });
        return Map.copyOf(oracles);
    }

    public record ChainInput(
            String name,
            List<String> rpcUrls,
            BigDecimal maxFeeCapGwei,
            Duration blockTime,
            Duration rpcTimeout,
            int rateLimitPerSecond,
            int rateLimitBurst) {}
}
