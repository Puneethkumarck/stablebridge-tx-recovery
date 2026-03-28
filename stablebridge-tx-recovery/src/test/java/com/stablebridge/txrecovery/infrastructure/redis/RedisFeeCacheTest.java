package com.stablebridge.txrecovery.infrastructure.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.stablebridge.txrecovery.domain.recovery.model.FeeEstimate;
import com.stablebridge.txrecovery.domain.recovery.model.FeeUrgency;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class RedisFeeCacheTest {

    private static final String SOME_CHAIN = "ethereum";
    private static final String SOME_CACHE_KEY = "str:gas:cache:ethereum";

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    private ObjectMapper objectMapper;
    private RedisFeeCache cache;

    @BeforeEach
    void setUp() {
        objectMapper = JsonMapper.builder().build();
        cache = new RedisFeeCache(redisTemplate, objectMapper);
    }

    private static FeeEstimate someFeeEstimate(FeeUrgency urgency) {
        return FeeEstimate.builder()
                .estimatedCost(new BigDecimal("0.005"))
                .denomination("ETH")
                .urgency(urgency)
                .details(Map.of("baseFee", "1000000000"))
                .build();
    }

    @Nested
    class Read {

        @Test
        void shouldReturnEstimateOnCacheHit() {
            // given
            var estimate = someFeeEstimate(FeeUrgency.FAST);
            var json = objectMapper.writeValueAsString(estimate);
            given(redisTemplate.opsForHash()).willReturn(hashOperations);
            given(hashOperations.get(SOME_CACHE_KEY, "FAST")).willReturn(json);

            // when
            var result = cache.read(SOME_CHAIN, FeeUrgency.FAST);

            // then
            assertThat(result).isPresent();
            var expected = someFeeEstimate(FeeUrgency.FAST);
            assertThat(result.get()).usingRecursiveComparison().isEqualTo(expected);
        }

        @Test
        void shouldReturnEmptyOnCacheMiss() {
            // given
            given(redisTemplate.opsForHash()).willReturn(hashOperations);
            given(hashOperations.get(SOME_CACHE_KEY, "SLOW")).willReturn(null);

            // when
            var result = cache.read(SOME_CHAIN, FeeUrgency.SLOW);

            // then
            assertThat(result).isEmpty();
        }

        @Test
        void shouldReturnEmptyWhenCachedValueIsNullString() {
            // given
            given(redisTemplate.opsForHash()).willReturn(hashOperations);
            given(hashOperations.get(SOME_CACHE_KEY, "FAST")).willReturn("null");

            // when
            var result = cache.read(SOME_CHAIN, FeeUrgency.FAST);

            // then
            assertThat(result).isEmpty();
        }

        @Test
        void shouldReturnEmptyOnRedisException() {
            // given
            given(redisTemplate.opsForHash()).willThrow(new RuntimeException("Redis down"));

            // when
            var result = cache.read(SOME_CHAIN, FeeUrgency.FAST);

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    class Write {

        @Test
        void shouldWriteEstimateAndSetTtl() {
            // given
            var estimate = someFeeEstimate(FeeUrgency.FAST);
            var json = objectMapper.writeValueAsString(estimate);
            var ttlMillis = 12_000L;
            given(redisTemplate.opsForHash()).willReturn(hashOperations);

            // when
            cache.write(SOME_CHAIN, FeeUrgency.FAST, estimate, ttlMillis);

            // then
            then(hashOperations).should().put(SOME_CACHE_KEY, "FAST", json);
            then(redisTemplate).should().expire(SOME_CACHE_KEY, ttlMillis, TimeUnit.MILLISECONDS);
        }

        @Test
        void shouldNotPropagateRedisException() {
            // given
            var estimate = someFeeEstimate(FeeUrgency.SLOW);
            given(redisTemplate.opsForHash()).willThrow(new RuntimeException("Redis down"));

            // when/then — no exception propagated
            cache.write(SOME_CHAIN, FeeUrgency.SLOW, estimate, 5_000L);
        }
    }
}
