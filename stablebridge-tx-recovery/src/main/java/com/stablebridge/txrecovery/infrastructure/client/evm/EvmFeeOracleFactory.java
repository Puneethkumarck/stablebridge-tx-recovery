package com.stablebridge.txrecovery.infrastructure.client.evm;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

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
        this.chainInputs = List.copyOf(Objects.requireNonNull(chainInputs));
        this.redisTemplate = Objects.requireNonNull(redisTemplate);
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.circuitBreakerRegistry = Objects.requireNonNull(circuitBreakerRegistry);
        this.rateLimiterRegistry = Objects.requireNonNull(rateLimiterRegistry);
    }

    public Map<String, FeeOracle> createAll() {
        return chainInputs.stream()
                .collect(Collectors.toUnmodifiableMap(
                        ChainInput::name,
                        this::createOracle));
    }

    private FeeOracle createOracle(ChainInput input) {
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

        return new EvmFeeOracle(rpcClient, properties, redisTemplate, objectMapper);
    }

    @lombok.Builder(toBuilder = true)
    public record ChainInput(
            String name,
            List<String> rpcUrls,
            BigDecimal maxFeeCapGwei,
            Duration blockTime,
            Duration rpcTimeout,
            int rateLimitPerSecond,
            int rateLimitBurst) {

        public ChainInput {
            Objects.requireNonNull(name);
            Objects.requireNonNull(rpcUrls);
            Objects.requireNonNull(maxFeeCapGwei);
            Objects.requireNonNull(blockTime);
            Objects.requireNonNull(rpcTimeout);
            rpcUrls = List.copyOf(rpcUrls);
        }
    }
}
