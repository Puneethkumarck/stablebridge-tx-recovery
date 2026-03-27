package com.stablebridge.txrecovery.infrastructure.client.evm;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class EvmFeeOracleFactoryTest {

    @Mock private StringRedisTemplate redisTemplate;
    private ObjectMapper objectMapper;
    private CircuitBreakerRegistry circuitBreakerRegistry;
    private RateLimiterRegistry rateLimiterRegistry;

    @BeforeEach void setUp() { objectMapper = JsonMapper.builder().build(); circuitBreakerRegistry = CircuitBreakerRegistry.ofDefaults(); rateLimiterRegistry = RateLimiterRegistry.ofDefaults(); }

    @Nested class CreateAll {
        @Test void shouldCreateOracleForEachChainInput() {
            var factory = new EvmFeeOracleFactory(List.of(new EvmFeeOracleFactory.ChainInput("ethereum", List.of("http://localhost:8545"), new BigDecimal("200"), Duration.ofSeconds(12), Duration.ofSeconds(5), 25, 50), new EvmFeeOracleFactory.ChainInput("base", List.of("http://localhost:8546"), new BigDecimal("5"), Duration.ofSeconds(2), Duration.ofSeconds(5), 25, 50), new EvmFeeOracleFactory.ChainInput("polygon", List.of("http://localhost:8547"), new BigDecimal("500"), Duration.ofSeconds(2), Duration.ofSeconds(5), 25, 50)), redisTemplate, objectMapper, circuitBreakerRegistry, rateLimiterRegistry);
            assertThat(factory.createAll()).hasSize(3).containsKeys("ethereum", "base", "polygon");
        }
        @Test void shouldReturnImmutableMap() {
            var factory = new EvmFeeOracleFactory(List.of(new EvmFeeOracleFactory.ChainInput("ethereum", List.of("http://localhost:8545"), new BigDecimal("200"), Duration.ofSeconds(12), Duration.ofSeconds(5), 25, 50)), redisTemplate, objectMapper, circuitBreakerRegistry, rateLimiterRegistry);
            assertThat(factory.createAll()).isUnmodifiable();
        }
        @Test void shouldReturnEmptyMapWhenNoChainInputs() { assertThat(new EvmFeeOracleFactory(List.of(), redisTemplate, objectMapper, circuitBreakerRegistry, rateLimiterRegistry).createAll()).isEmpty(); }
        @Test void shouldCreateOraclesWithCorrectChainNames() {
            var factory = new EvmFeeOracleFactory(List.of(new EvmFeeOracleFactory.ChainInput("ethereum", List.of("http://localhost:8545"), new BigDecimal("200"), Duration.ofSeconds(12), Duration.ofSeconds(5), 25, 50), new EvmFeeOracleFactory.ChainInput("polygon", List.of("http://localhost:8547"), new BigDecimal("500"), Duration.ofSeconds(2), Duration.ofSeconds(5), 25, 50)), redisTemplate, objectMapper, circuitBreakerRegistry, rateLimiterRegistry);
            factory.createAll().forEach((chain, oracle) -> assertThat(((EvmFeeOracle) oracle).getChain()).isEqualTo(chain));
        }
    }

    @Nested class ChainInputRecord {
        @Test void shouldPreserveAllFields() {
            var result = new EvmFeeOracleFactory.ChainInput("ethereum", List.of("http://localhost:8545", "http://localhost:8546"), new BigDecimal("200"), Duration.ofSeconds(12), Duration.ofSeconds(5), 25, 50);
            assertThat(result).usingRecursiveComparison().isEqualTo(new EvmFeeOracleFactory.ChainInput("ethereum", List.of("http://localhost:8545", "http://localhost:8546"), new BigDecimal("200"), Duration.ofSeconds(12), Duration.ofSeconds(5), 25, 50));
        }
    }
}
