package com.stablebridge.txrecovery.infrastructure.client.evm;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import com.stablebridge.txrecovery.domain.recovery.port.FeeOracle;
import com.stablebridge.txrecovery.infrastructure.redis.RedisFeeCache;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import tools.jackson.databind.ObjectMapper;

public class EvmFeeOracleFactory {

    private final List<ChainInput> chainInputs;
    private final RedisFeeCache feeCache;
    private final ObjectMapper objectMapper;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final RateLimiterRegistry rateLimiterRegistry;

    public EvmFeeOracleFactory(
            List<ChainInput> chainInputs,
            RedisFeeCache feeCache,
            ObjectMapper objectMapper,
            CircuitBreakerRegistry circuitBreakerRegistry,
            RateLimiterRegistry rateLimiterRegistry) {
        this.chainInputs = List.copyOf(Objects.requireNonNull(chainInputs));
        this.feeCache = Objects.requireNonNull(feeCache);
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.circuitBreakerRegistry = Objects.requireNonNull(circuitBreakerRegistry);
        this.rateLimiterRegistry = Objects.requireNonNull(rateLimiterRegistry);
    }

    public Map<String, EvmRpcClient> createRpcClients() {
        return chainInputs.stream()
                .collect(Collectors.toUnmodifiableMap(
                        ChainInput::name,
                        this::createRpcClient));
    }

    public Map<String, FeeOracle> createOracles(Map<String, EvmRpcClient> rpcClients) {
        return chainInputs.stream()
                .collect(Collectors.toUnmodifiableMap(
                        ChainInput::name,
                        input -> createOracle(input, rpcClients.get(input.name()))));
    }

    private EvmRpcClient createRpcClient(ChainInput input) {
        return new EvmRpcClient(
                input.name(),
                input.rpcUrls(),
                input.rpcTimeout(),
                input.rateLimitPerSecond(),
                input.rateLimitBurst(),
                circuitBreakerRegistry,
                rateLimiterRegistry,
                objectMapper);
    }

    private FeeOracle createOracle(ChainInput input, EvmRpcClient rpcClient) {
        var properties = EvmChainProperties.builder()
                .chain(input.name())
                .maxFeeCapGwei(input.maxFeeCapGwei())
                .blockTime(input.blockTime())
                .build();

        return new EvmFeeOracle(rpcClient, properties, feeCache);
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
