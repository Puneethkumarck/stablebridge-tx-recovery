package com.stablebridge.txrecovery.infrastructure.client.evm;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.validation.annotation.Validated;

import com.stablebridge.txrecovery.domain.address.port.OnChainNonceProvider;
import com.stablebridge.txrecovery.domain.recovery.port.FeeOracle;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

@Configuration
@Slf4j
public class EvmFeeOracleConfig {

    @Bean
    @Validated
    @ConfigurationProperties(prefix = "str.evm")
    EvmFeeOracleSettings evmFeeOracleSettings() {
        return new EvmFeeOracleSettings();
    }

    @Bean
    @ConditionalOnBean(StringRedisTemplate.class)
    EvmFeeOracleFactory evmFeeOracleFactory(
            EvmFeeOracleSettings settings,
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            CircuitBreakerRegistry circuitBreakerRegistry,
            RateLimiterRegistry rateLimiterRegistry) {
        validateNoDuplicateChains(settings.getChains());
        var chainInputs = settings.getChains().stream()
                .map(c -> new EvmFeeOracleFactory.ChainInput(
                        c.getName(),
                        c.getRpcUrls(),
                        c.getMaxFeeCapGwei(),
                        c.getBlockTime(),
                        c.getRpcTimeout(),
                        c.getRateLimitPerSecond(),
                        c.getRateLimitBurst()))
                .toList();
        return new EvmFeeOracleFactory(
                chainInputs,
                redisTemplate,
                objectMapper,
                circuitBreakerRegistry,
                rateLimiterRegistry);
    }

    @Bean
    @ConditionalOnBean(EvmFeeOracleFactory.class)
    Map<String, EvmRpcClient> evmRpcClients(EvmFeeOracleFactory factory) {
        var clients = factory.createRpcClients();
        log.info("Created EVM RPC clients for chains: {}", clients.keySet());
        return clients;
    }

    @Bean
    @ConditionalOnBean(EvmFeeOracleFactory.class)
    Map<String, FeeOracle> evmFeeOracles(EvmFeeOracleFactory factory, Map<String, EvmRpcClient> evmRpcClients) {
        var oracles = factory.createOracles(evmRpcClients);
        log.info("Created EVM fee oracles for chains: {}", oracles.keySet());
        return oracles;
    }

    @Bean
    @ConditionalOnBean(name = "evmRpcClients")
    OnChainNonceProvider onChainNonceProvider(Map<String, EvmRpcClient> evmRpcClients) {
        return new EvmOnChainNonceProvider(evmRpcClients);
    }

    private static void validateNoDuplicateChains(List<ChainSettings> chains) {
        var seen = new HashSet<String>();
        var duplicates = chains.stream()
                .map(ChainSettings::getName)
                .filter(name -> name != null && !seen.add(name))
                .distinct()
                .toList();
        if (!duplicates.isEmpty()) {
            throw new IllegalStateException("Duplicate EVM chain names: " + duplicates);
        }
    }

    @Getter
    @Setter
    public static class EvmFeeOracleSettings {
        @Valid
        private List<ChainSettings> chains = List.of();
    }

    @Getter
    @Setter
    public static class ChainSettings {
        @NotBlank
        private String name;
        @NotEmpty
        private List<String> rpcUrls = List.of();
        @NotNull
        private BigDecimal maxFeeCapGwei = new BigDecimal("200");
        @NotNull
        private Duration blockTime = Duration.ofSeconds(12);
        @NotNull
        private Duration rpcTimeout = Duration.ofSeconds(5);
        private int rateLimitPerSecond = 25;
        private int rateLimitBurst = 50;
    }
}
