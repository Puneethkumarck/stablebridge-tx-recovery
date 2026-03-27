package com.stablebridge.txrecovery.application.config;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.stablebridge.txrecovery.domain.recovery.port.FeeOracle;
import com.stablebridge.txrecovery.infrastructure.client.evm.EvmFeeOracleFactory;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

@Configuration
@Slf4j
public class EvmFeeOracleConfig {

    @Bean
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
    Map<String, FeeOracle> evmFeeOracles(EvmFeeOracleFactory factory) {
        var oracles = factory.createAll();
        log.info("Created EVM fee oracles for chains: {}", oracles.keySet());
        return oracles;
    }

    public static class EvmFeeOracleSettings {

        private List<ChainSettings> chains = List.of();

        public List<ChainSettings> getChains() {
            return chains;
        }

        public void setChains(List<ChainSettings> chains) {
            this.chains = chains;
        }
    }

    public static class ChainSettings {

        private String name;
        private List<String> rpcUrls = List.of();
        private BigDecimal maxFeeCapGwei = new BigDecimal("200");
        private Duration blockTime = Duration.ofSeconds(12);
        private Duration rpcTimeout = Duration.ofSeconds(5);
        private int rateLimitPerSecond = 25;
        private int rateLimitBurst = 50;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public List<String> getRpcUrls() {
            return rpcUrls;
        }

        public void setRpcUrls(List<String> rpcUrls) {
            this.rpcUrls = rpcUrls;
        }

        public BigDecimal getMaxFeeCapGwei() {
            return maxFeeCapGwei;
        }

        public void setMaxFeeCapGwei(BigDecimal maxFeeCapGwei) {
            this.maxFeeCapGwei = maxFeeCapGwei;
        }

        public Duration getBlockTime() {
            return blockTime;
        }

        public void setBlockTime(Duration blockTime) {
            this.blockTime = blockTime;
        }

        public Duration getRpcTimeout() {
            return rpcTimeout;
        }

        public void setRpcTimeout(Duration rpcTimeout) {
            this.rpcTimeout = rpcTimeout;
        }

        public int getRateLimitPerSecond() {
            return rateLimitPerSecond;
        }

        public void setRateLimitPerSecond(int rateLimitPerSecond) {
            this.rateLimitPerSecond = rateLimitPerSecond;
        }

        public int getRateLimitBurst() {
            return rateLimitBurst;
        }

        public void setRateLimitBurst(int rateLimitBurst) {
            this.rateLimitBurst = rateLimitBurst;
        }
    }
}
