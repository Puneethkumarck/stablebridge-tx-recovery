package com.stablebridge.txrecovery.infrastructure.redis;

import static com.stablebridge.txrecovery.testutil.stubs.EvmRpcStubs.stubJsonRpcResponse;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.stablebridge.txrecovery.domain.address.model.NonceAllocation;
import com.stablebridge.txrecovery.domain.exception.NonceConcurrencyException;
import com.stablebridge.txrecovery.infrastructure.client.evm.EvmRpcClient;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;

@Testcontainers
class RedisNonceManagerIntegrationTest {

    private static final String ADDRESS = "0x742d35Cc6634C0532925a3b844Bc9e7595f2bD28";
    private static final String CHAIN = "ethereum_mainnet";
    private static final int REDIS_PORT = 6379;

    @SuppressWarnings("resource")
    @Container
    private static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7-alpine").withExposedPorts(REDIS_PORT);

    private WireMockServer wireMockServer;
    private StringRedisTemplate redisTemplate;
    private RedisNonceManager nonceManager;

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMockServer.start();
        var redisConfig = new RedisStandaloneConfiguration(REDIS.getHost(), REDIS.getMappedPort(REDIS_PORT));
        var connectionFactory = new LettuceConnectionFactory(redisConfig);
        connectionFactory.afterPropertiesSet();
        redisTemplate = new StringRedisTemplate(connectionFactory);
        var evmRpcClient = new EvmRpcClient(
                CHAIN,
                List.of(wireMockServer.baseUrl()),
                Duration.ofSeconds(5),
                100,
                100,
                CircuitBreakerRegistry.ofDefaults(),
                RateLimiterRegistry.ofDefaults());
        nonceManager = new RedisNonceManager(redisTemplate, evmRpcClient);
    }

    @AfterEach
    void tearDown() {
        redisTemplate.delete(RedisKeyNamespace.nonceHash(CHAIN, ADDRESS));
        redisTemplate.delete(RedisKeyNamespace.nonceInflightSet(CHAIN, ADDRESS));
        wireMockServer.stop();
    }

    @Nested
    class AllocateIntegration {

        @Test
        void shouldAllocateFirstNonceFromOnChain() {
            // given
            stubJsonRpcResponse(wireMockServer, "eth_getTransactionCount", "\"0x5\"");

            // when
            var result = nonceManager.allocate(ADDRESS, CHAIN);

            // then
            var expected = NonceAllocation.builder().address(ADDRESS).chain(CHAIN).nonce(5L).build();
            assertThat(result).usingRecursiveComparison().isEqualTo(expected);
        }

        @Test
        void shouldAllocateSubsequentNonceIncrementingAllocated() {
            // given
            stubJsonRpcResponse(wireMockServer, "eth_getTransactionCount", "\"0x5\"");
            nonceManager.allocate(ADDRESS, CHAIN);

            // when
            var result = nonceManager.allocate(ADDRESS, CHAIN);

            // then
            var expected = NonceAllocation.builder().address(ADDRESS).chain(CHAIN).nonce(6L).build();
            assertThat(result).usingRecursiveComparison().isEqualTo(expected);
        }

        @Test
        void shouldSelfCorrectWhenOnChainNonceIsAhead() {
            // given
            stubJsonRpcResponse(wireMockServer, "eth_getTransactionCount", "\"0x5\"");
            nonceManager.allocate(ADDRESS, CHAIN);
            wireMockServer.resetAll();
            stubJsonRpcResponse(wireMockServer, "eth_getTransactionCount", "\"0xa\"");

            // when
            var result = nonceManager.allocate(ADDRESS, CHAIN);

            // then
            var expected = NonceAllocation.builder().address(ADDRESS).chain(CHAIN).nonce(10L).build();
            assertThat(result).usingRecursiveComparison().isEqualTo(expected);
        }

        @Test
        void shouldTrackAllocatedNonceInInflightSet() {
            // given
            stubJsonRpcResponse(wireMockServer, "eth_getTransactionCount", "\"0x0\"");

            // when
            nonceManager.allocate(ADDRESS, CHAIN);

            // then
            var members = redisTemplate.opsForSet().members(RedisKeyNamespace.nonceInflightSet(CHAIN, ADDRESS));
            assertThat(members).contains("0");
        }

        @Test
        void shouldAllocateMultipleNoncesSequentially() {
            // given
            stubJsonRpcResponse(wireMockServer, "eth_getTransactionCount", "\"0x0\"");

            // when
            var first = nonceManager.allocate(ADDRESS, CHAIN);
            var second = nonceManager.allocate(ADDRESS, CHAIN);
            var third = nonceManager.allocate(ADDRESS, CHAIN);

            // then
            assertThat(first.nonce()).isEqualTo(0L);
            assertThat(second.nonce()).isEqualTo(1L);
            assertThat(third.nonce()).isEqualTo(2L);
            var members = redisTemplate.opsForSet().members(RedisKeyNamespace.nonceInflightSet(CHAIN, ADDRESS));
            assertThat(members).containsExactlyInAnyOrder("0", "1", "2");
        }
    }

    @Nested
    class ReleaseIntegration {

        @Test
        void shouldRemoveNonceFromInflightSetWithoutDecrementingAllocated() {
            // given
            stubJsonRpcResponse(wireMockServer, "eth_getTransactionCount", "\"0x5\"");
            var allocation = nonceManager.allocate(ADDRESS, CHAIN);

            // when
            nonceManager.release(allocation);

            // then
            var members = redisTemplate.opsForSet().members(RedisKeyNamespace.nonceInflightSet(CHAIN, ADDRESS));
            assertThat(members).doesNotContain("5");
            var allocated = redisTemplate.opsForHash().get(RedisKeyNamespace.nonceHash(CHAIN, ADDRESS), "allocated");
            assertThat(allocated).isEqualTo("5");
        }
    }

    @Nested
    class ConfirmIntegration {

        @Test
        void shouldUpdateConfirmedFieldMonotonically() {
            // given
            stubJsonRpcResponse(wireMockServer, "eth_getTransactionCount", "\"0x0\"");
            nonceManager.allocate(ADDRESS, CHAIN);
            var second = nonceManager.allocate(ADDRESS, CHAIN);

            // when
            nonceManager.confirm(second);
            nonceManager.confirm(NonceAllocation.builder().address(ADDRESS).chain(CHAIN).nonce(0L).build());

            // then
            var confirmed = redisTemplate.opsForHash().get(RedisKeyNamespace.nonceHash(CHAIN, ADDRESS), "confirmed");
            assertThat(confirmed).isEqualTo("1");
        }

        @Test
        void shouldRemoveFromInflightSetOnConfirm() {
            // given
            stubJsonRpcResponse(wireMockServer, "eth_getTransactionCount", "\"0x0\"");
            var allocation = nonceManager.allocate(ADDRESS, CHAIN);

            // when
            nonceManager.confirm(allocation);

            // then
            var members = redisTemplate.opsForSet().members(RedisKeyNamespace.nonceInflightSet(CHAIN, ADDRESS));
            assertThat(members).doesNotContain("0");
        }
    }

    @Nested
    class SyncFromChainIntegration {

        @Test
        void shouldResetStateFromOnChain() {
            // given
            stubJsonRpcResponse(wireMockServer, "eth_getTransactionCount", "\"0x5\"");
            nonceManager.allocate(ADDRESS, CHAIN);
            nonceManager.allocate(ADDRESS, CHAIN);
            wireMockServer.resetAll();
            stubJsonRpcResponse(wireMockServer, "eth_getTransactionCount", "\"0xa\"");

            // when
            nonceManager.syncFromChain(ADDRESS, CHAIN);

            // then
            var hashKey = RedisKeyNamespace.nonceHash(CHAIN, ADDRESS);
            assertThat(redisTemplate.opsForHash().get(hashKey, "allocated")).isEqualTo("9");
            assertThat(redisTemplate.opsForHash().get(hashKey, "confirmed")).isEqualTo("9");
            assertThat(redisTemplate.hasKey(RedisKeyNamespace.nonceInflightSet(CHAIN, ADDRESS))).isFalse();
        }
    }

    @Nested
    class DetectGapsIntegration {

        @Test
        void shouldDetectGapNonces() {
            // given
            stubJsonRpcResponse(wireMockServer, "eth_getTransactionCount", "\"0x0\"");
            var first = nonceManager.allocate(ADDRESS, CHAIN);
            nonceManager.allocate(ADDRESS, CHAIN);
            nonceManager.allocate(ADDRESS, CHAIN);
            nonceManager.confirm(first);
            wireMockServer.resetAll();
            stubJsonRpcResponse(wireMockServer, "eth_getTransactionCount", "\"0x2\"");

            // when
            var gaps = nonceManager.detectGaps(ADDRESS, CHAIN);

            // then
            assertThat(gaps).containsExactly(1L);
        }

        @Test
        void shouldReturnEmptyWhenNoGaps() {
            // given
            stubJsonRpcResponse(wireMockServer, "eth_getTransactionCount", "\"0x0\"");

            // when
            var gaps = nonceManager.detectGaps(ADDRESS, CHAIN);

            // then
            assertThat(gaps).isEmpty();
        }
    }

    @Nested
    class ConcurrencyIntegration {

        @Test
        void shouldHandleConcurrentAllocationsWithoutDuplicates() throws InterruptedException {
            // given
            stubJsonRpcResponse(wireMockServer, "eth_getTransactionCount", "\"0x0\"");
            var threadCount = 10;
            var latch = new CountDownLatch(threadCount);
            var allocations = new CopyOnWriteArrayList<NonceAllocation>();
            var exceptions = new CopyOnWriteArrayList<Exception>();

            // when
            try (var executor = Executors.newFixedThreadPool(threadCount)) {
                for (var i = 0; i < threadCount; i++) {
                    executor.submit(() -> {
                        try {
                            allocations.add(nonceManager.allocate(ADDRESS, CHAIN));
                        } catch (NonceConcurrencyException e) {
                            exceptions.add(e);
                        } finally {
                            latch.countDown();
                        }
                    });
                }
                latch.await();
            }

            // then
            assertThat(allocations.size() + exceptions.size()).isEqualTo(threadCount);
            assertThat(allocations.stream().map(NonceAllocation::nonce).toList()).doesNotHaveDuplicates();
        }
    }
}
