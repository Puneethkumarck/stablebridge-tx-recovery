package com.stablebridge.txrecovery.infrastructure.redis;

import static com.stablebridge.txrecovery.testutil.fixtures.NonceAllocationFixtures.SOME_ADDRESS;
import static com.stablebridge.txrecovery.testutil.fixtures.NonceAllocationFixtures.SOME_CHAIN;
import static com.stablebridge.txrecovery.testutil.fixtures.NonceAllocationFixtures.someAllocation;
import static com.stablebridge.txrecovery.testutil.stubs.EvmRpcStubs.stubJsonRpcResponse;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Map;
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
import com.stablebridge.txrecovery.infrastructure.client.evm.EvmOnChainNonceProvider;
import com.stablebridge.txrecovery.infrastructure.client.evm.EvmRpcClient;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

@Testcontainers
class RedisNonceManagerIntegrationTest {

    private static final int REDIS_PORT = 6379;

    @SuppressWarnings("resource")
    @Container
    private static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7-alpine").withExposedPorts(REDIS_PORT);

    private WireMockServer wireMockServer;
    private StringRedisTemplate redisTemplate;
    private SimpleMeterRegistry meterRegistry;
    private RedisNonceManager nonceManager;

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMockServer.start();
        var redisConfig = new RedisStandaloneConfiguration(REDIS.getHost(), REDIS.getMappedPort(REDIS_PORT));
        var connectionFactory = new LettuceConnectionFactory(redisConfig);
        connectionFactory.afterPropertiesSet();
        redisTemplate = new StringRedisTemplate(connectionFactory);
        meterRegistry = new SimpleMeterRegistry();
        var objectMapper = JsonMapper.builder()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
        var evmRpcClient = new EvmRpcClient(
                SOME_CHAIN,
                List.of(wireMockServer.baseUrl()),
                Duration.ofSeconds(5),
                100,
                100,
                CircuitBreakerRegistry.ofDefaults(),
                RateLimiterRegistry.ofDefaults(),
                objectMapper);
        var onChainNonceProvider = new EvmOnChainNonceProvider(Map.of(SOME_CHAIN, evmRpcClient));
        nonceManager = new RedisNonceManager(redisTemplate, onChainNonceProvider, meterRegistry);
    }

    @AfterEach
    void tearDown() {
        redisTemplate.delete(RedisKeyNamespace.nonceHash(SOME_CHAIN, SOME_ADDRESS));
        redisTemplate.delete(RedisKeyNamespace.nonceInflightSet(SOME_CHAIN, SOME_ADDRESS));
        wireMockServer.stop();
    }

    @Nested
    class AllocateIntegration {

        @Test
        void shouldAllocateFirstNonceFromOnChain() {
            // given
            stubJsonRpcResponse(wireMockServer, "eth_getTransactionCount", "\"0x5\"");

            // when
            var result = nonceManager.allocate(SOME_ADDRESS, SOME_CHAIN);

            // then
            var expected = someAllocation(5L);
            assertThat(result).usingRecursiveComparison().isEqualTo(expected);
        }

        @Test
        void shouldAllocateSubsequentNonceIncrementingAllocated() {
            // given
            stubJsonRpcResponse(wireMockServer, "eth_getTransactionCount", "\"0x5\"");
            nonceManager.allocate(SOME_ADDRESS, SOME_CHAIN);

            // when
            var result = nonceManager.allocate(SOME_ADDRESS, SOME_CHAIN);

            // then
            var expected = someAllocation(6L);
            assertThat(result).usingRecursiveComparison().isEqualTo(expected);
        }

        @Test
        void shouldSelfCorrectWhenOnChainNonceIsAhead() {
            // given
            stubJsonRpcResponse(wireMockServer, "eth_getTransactionCount", "\"0x5\"");
            nonceManager.allocate(SOME_ADDRESS, SOME_CHAIN);
            wireMockServer.resetAll();
            stubJsonRpcResponse(wireMockServer, "eth_getTransactionCount", "\"0xa\"");

            // when
            var result = nonceManager.allocate(SOME_ADDRESS, SOME_CHAIN);

            // then
            var expected = someAllocation(10L);
            assertThat(result).usingRecursiveComparison().isEqualTo(expected);
        }

        @Test
        void shouldTrackAllocatedNonceInInflightSet() {
            // given
            stubJsonRpcResponse(wireMockServer, "eth_getTransactionCount", "\"0x0\"");

            // when
            nonceManager.allocate(SOME_ADDRESS, SOME_CHAIN);

            // then
            var members = redisTemplate.opsForSet().members(RedisKeyNamespace.nonceInflightSet(SOME_CHAIN, SOME_ADDRESS));
            assertThat(members).contains("0");
        }

        @Test
        void shouldAllocateMultipleNoncesSequentially() {
            // given
            stubJsonRpcResponse(wireMockServer, "eth_getTransactionCount", "\"0x0\"");

            // when
            var first = nonceManager.allocate(SOME_ADDRESS, SOME_CHAIN);
            var second = nonceManager.allocate(SOME_ADDRESS, SOME_CHAIN);
            var third = nonceManager.allocate(SOME_ADDRESS, SOME_CHAIN);

            // then
            var expectedFirst = someAllocation(0L);
            var expectedSecond = someAllocation(1L);
            var expectedThird = someAllocation(2L);
            assertThat(first).usingRecursiveComparison().isEqualTo(expectedFirst);
            assertThat(second).usingRecursiveComparison().isEqualTo(expectedSecond);
            assertThat(third).usingRecursiveComparison().isEqualTo(expectedThird);
            var members = redisTemplate.opsForSet().members(RedisKeyNamespace.nonceInflightSet(SOME_CHAIN, SOME_ADDRESS));
            assertThat(members).containsExactlyInAnyOrder("0", "1", "2");
        }
    }

    @Nested
    class ReleaseIntegration {

        @Test
        void shouldRemoveNonceFromInflightSetWithoutDecrementingAllocated() {
            // given
            stubJsonRpcResponse(wireMockServer, "eth_getTransactionCount", "\"0x5\"");
            var allocation = nonceManager.allocate(SOME_ADDRESS, SOME_CHAIN);

            // when
            nonceManager.release(allocation);

            // then
            var members = redisTemplate.opsForSet().members(RedisKeyNamespace.nonceInflightSet(SOME_CHAIN, SOME_ADDRESS));
            assertThat(members).doesNotContain("5");
            var allocated = redisTemplate.opsForHash().get(RedisKeyNamespace.nonceHash(SOME_CHAIN, SOME_ADDRESS), "allocated");
            assertThat(allocated).isEqualTo("5");
        }
    }

    @Nested
    class ConfirmIntegration {

        @Test
        void shouldUpdateConfirmedFieldMonotonically() {
            // given
            stubJsonRpcResponse(wireMockServer, "eth_getTransactionCount", "\"0x0\"");
            nonceManager.allocate(SOME_ADDRESS, SOME_CHAIN);
            var second = nonceManager.allocate(SOME_ADDRESS, SOME_CHAIN);

            // when
            nonceManager.confirm(second);
            nonceManager.confirm(someAllocation(0L));

            // then
            var confirmed = redisTemplate.opsForHash().get(RedisKeyNamespace.nonceHash(SOME_CHAIN, SOME_ADDRESS), "confirmed");
            assertThat(confirmed).isEqualTo("1");
        }

        @Test
        void shouldRemoveFromInflightSetOnConfirm() {
            // given
            stubJsonRpcResponse(wireMockServer, "eth_getTransactionCount", "\"0x0\"");
            var allocation = nonceManager.allocate(SOME_ADDRESS, SOME_CHAIN);

            // when
            nonceManager.confirm(allocation);

            // then
            var members = redisTemplate.opsForSet().members(RedisKeyNamespace.nonceInflightSet(SOME_CHAIN, SOME_ADDRESS));
            assertThat(members).doesNotContain("0");
        }
    }

    @Nested
    class SyncFromChainIntegration {

        @Test
        void shouldResetStateFromOnChain() {
            // given
            stubJsonRpcResponse(wireMockServer, "eth_getTransactionCount", "\"0x5\"");
            nonceManager.allocate(SOME_ADDRESS, SOME_CHAIN);
            nonceManager.allocate(SOME_ADDRESS, SOME_CHAIN);
            wireMockServer.resetAll();
            stubJsonRpcResponse(wireMockServer, "eth_getTransactionCount", "\"0xa\"");

            // when
            nonceManager.syncFromChain(SOME_ADDRESS, SOME_CHAIN);

            // then
            var hashKey = RedisKeyNamespace.nonceHash(SOME_CHAIN, SOME_ADDRESS);
            assertThat(redisTemplate.opsForHash().get(hashKey, "allocated")).isEqualTo("9");
            assertThat(redisTemplate.opsForHash().get(hashKey, "confirmed")).isEqualTo("9");
            assertThat(redisTemplate.hasKey(RedisKeyNamespace.nonceInflightSet(SOME_CHAIN, SOME_ADDRESS))).isFalse();
        }
    }

    @Nested
    class DetectGapsIntegration {

        @Test
        void shouldDetectGapNonces() {
            // given
            stubJsonRpcResponse(wireMockServer, "eth_getTransactionCount", "\"0x0\"");
            var first = nonceManager.allocate(SOME_ADDRESS, SOME_CHAIN);
            nonceManager.allocate(SOME_ADDRESS, SOME_CHAIN);
            nonceManager.allocate(SOME_ADDRESS, SOME_CHAIN);
            nonceManager.confirm(first);
            wireMockServer.resetAll();
            stubJsonRpcResponse(wireMockServer, "eth_getTransactionCount", "\"0x2\"");

            // when
            var gaps = nonceManager.detectGaps(SOME_ADDRESS, SOME_CHAIN);

            // then
            assertThat(gaps).containsExactly(1L);
        }

        @Test
        void shouldReturnEmptyWhenNoGaps() {
            // given
            stubJsonRpcResponse(wireMockServer, "eth_getTransactionCount", "\"0x0\"");

            // when
            var gaps = nonceManager.detectGaps(SOME_ADDRESS, SOME_CHAIN);

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
            var startGate = new CountDownLatch(1);
            var latch = new CountDownLatch(threadCount);
            var allocations = new CopyOnWriteArrayList<NonceAllocation>();
            var exceptions = new CopyOnWriteArrayList<Exception>();

            // when
            try (var executor = Executors.newFixedThreadPool(threadCount)) {
                for (var i = 0; i < threadCount; i++) {
                    executor.submit(() -> {
                        try {
                            startGate.await();
                            allocations.add(nonceManager.allocate(SOME_ADDRESS, SOME_CHAIN));
                        } catch (NonceConcurrencyException e) {
                            exceptions.add(e);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            exceptions.add(e);
                        } finally {
                            latch.countDown();
                        }
                    });
                }
                startGate.countDown();
                latch.await();
            }

            // then
            assertThat(allocations.size() + exceptions.size()).isEqualTo(threadCount);
            assertThat(allocations.stream().map(NonceAllocation::nonce).toList()).doesNotHaveDuplicates();
        }
    }
}
