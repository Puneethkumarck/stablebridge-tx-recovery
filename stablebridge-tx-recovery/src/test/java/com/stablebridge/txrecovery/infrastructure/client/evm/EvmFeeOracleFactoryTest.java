package com.stablebridge.txrecovery.infrastructure.client.evm;

import static com.stablebridge.txrecovery.infrastructure.client.evm.EvmFeeOracleFixtures.SOME_BASE_CHAIN_INPUT;
import static com.stablebridge.txrecovery.infrastructure.client.evm.EvmFeeOracleFixtures.SOME_ETHEREUM_CHAIN_INPUT;
import static com.stablebridge.txrecovery.infrastructure.client.evm.EvmFeeOracleFixtures.SOME_POLYGON_CHAIN_INPUT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Mock
    private StringRedisTemplate redisTemplate;

    private ObjectMapper objectMapper;
    private CircuitBreakerRegistry circuitBreakerRegistry;
    private RateLimiterRegistry rateLimiterRegistry;

    @BeforeEach
    void setUp() {
        objectMapper = JsonMapper.builder().build();
        circuitBreakerRegistry = CircuitBreakerRegistry.ofDefaults();
        rateLimiterRegistry = RateLimiterRegistry.ofDefaults();
    }

    @Nested
    class CreateAll {

        @Test
        void shouldCreateOracleForEachChainInput() {
            // given
            var factory = new EvmFeeOracleFactory(
                    List.of(SOME_ETHEREUM_CHAIN_INPUT, SOME_BASE_CHAIN_INPUT, SOME_POLYGON_CHAIN_INPUT),
                    redisTemplate,
                    objectMapper,
                    circuitBreakerRegistry,
                    rateLimiterRegistry);

            // when
            var result = factory.createAll();

            // then
            assertThat(result).hasSize(3).containsKeys("ethereum", "base", "polygon");
        }

        @Test
        void shouldReturnImmutableMap() {
            // given
            var factory = new EvmFeeOracleFactory(
                    List.of(SOME_ETHEREUM_CHAIN_INPUT),
                    redisTemplate,
                    objectMapper,
                    circuitBreakerRegistry,
                    rateLimiterRegistry);

            // when
            var result = factory.createAll();

            // then
            assertThat(result).isUnmodifiable();
        }

        @Test
        void shouldReturnEmptyMapWhenNoChainInputs() {
            // given
            var factory = new EvmFeeOracleFactory(
                    List.of(),
                    redisTemplate,
                    objectMapper,
                    circuitBreakerRegistry,
                    rateLimiterRegistry);

            // when
            var result = factory.createAll();

            // then
            assertThat(result).isEmpty();
        }

        @Test
        void shouldCreateOraclesWithCorrectChainNames() {
            // given
            var factory = new EvmFeeOracleFactory(
                    List.of(SOME_ETHEREUM_CHAIN_INPUT, SOME_POLYGON_CHAIN_INPUT),
                    redisTemplate,
                    objectMapper,
                    circuitBreakerRegistry,
                    rateLimiterRegistry);

            // when
            var result = factory.createAll();

            // then
            result.forEach((chain, oracle) ->
                    assertThat(((EvmFeeOracle) oracle).getChain()).isEqualTo(chain));
        }
    }

    @Nested
    class ChainInputRecord {

        @Test
        void shouldPreserveAllFields() {
            // given
            var input = new EvmFeeOracleFactory.ChainInput(
                    "ethereum",
                    List.of("http://localhost:8545", "http://localhost:8546"),
                    new BigDecimal("200"),
                    Duration.ofSeconds(12),
                    Duration.ofSeconds(5),
                    25,
                    50);

            // when/then
            var expected = new EvmFeeOracleFactory.ChainInput(
                    "ethereum",
                    List.of("http://localhost:8545", "http://localhost:8546"),
                    new BigDecimal("200"),
                    Duration.ofSeconds(12),
                    Duration.ofSeconds(5),
                    25,
                    50);
            assertThat(input).usingRecursiveComparison().isEqualTo(expected);
        }

        @Test
        void shouldThrowWhenNameIsNull() {
            // when/then
            assertThatThrownBy(() -> EvmFeeOracleFactory.ChainInput.builder()
                    .name(null)
                    .rpcUrls(List.of("http://localhost:8545"))
                    .maxFeeCapGwei(new BigDecimal("200"))
                    .blockTime(Duration.ofSeconds(12))
                    .rpcTimeout(Duration.ofSeconds(5))
                    .rateLimitPerSecond(25)
                    .rateLimitBurst(50)
                    .build())
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        void shouldThrowWhenRpcUrlsIsNull() {
            // when/then
            assertThatThrownBy(() -> EvmFeeOracleFactory.ChainInput.builder()
                    .name("ethereum")
                    .rpcUrls(null)
                    .maxFeeCapGwei(new BigDecimal("200"))
                    .blockTime(Duration.ofSeconds(12))
                    .rpcTimeout(Duration.ofSeconds(5))
                    .rateLimitPerSecond(25)
                    .rateLimitBurst(50)
                    .build())
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        void shouldReturnImmutableRpcUrlsList() {
            // given
            var input = SOME_ETHEREUM_CHAIN_INPUT;

            // when/then
            assertThat(input.rpcUrls()).isUnmodifiable();
        }
    }

    @Nested
    class ConstructorValidation {

        @Test
        void shouldThrowWhenChainInputsIsNull() {
            // when/then
            assertThatThrownBy(() -> new EvmFeeOracleFactory(
                    null,
                    redisTemplate,
                    objectMapper,
                    circuitBreakerRegistry,
                    rateLimiterRegistry))
                    .isInstanceOf(NullPointerException.class);
        }
    }
}
