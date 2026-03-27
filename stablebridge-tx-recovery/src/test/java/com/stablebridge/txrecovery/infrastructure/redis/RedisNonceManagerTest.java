package com.stablebridge.txrecovery.infrastructure.redis;

import static com.stablebridge.txrecovery.testutil.fixtures.NonceAllocationFixtures.SOME_ADDRESS;
import static com.stablebridge.txrecovery.testutil.fixtures.NonceAllocationFixtures.SOME_CHAIN;
import static com.stablebridge.txrecovery.testutil.fixtures.NonceAllocationFixtures.SOME_HASH_KEY;
import static com.stablebridge.txrecovery.testutil.fixtures.NonceAllocationFixtures.SOME_INFLIGHT_KEY;
import static com.stablebridge.txrecovery.testutil.fixtures.NonceAllocationFixtures.someAllocation;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.math.BigInteger;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import com.stablebridge.txrecovery.domain.address.model.NonceAllocation;
import com.stablebridge.txrecovery.domain.address.port.OnChainNonceProvider;
import com.stablebridge.txrecovery.domain.exception.NonceConcurrencyException;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

@ExtendWith(MockitoExtension.class)
class RedisNonceManagerTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private OnChainNonceProvider onChainNonceProvider;
    @Mock private HashOperations<String, Object, Object> hashOperations;
    @Mock private SetOperations<String, String> setOperations;

    private SimpleMeterRegistry meterRegistry;
    private RedisNonceManager nonceManager;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        nonceManager = new RedisNonceManager(redisTemplate, onChainNonceProvider, meterRegistry);
    }

    @Nested
    class Allocate {

        @SuppressWarnings("rawtypes")
        private final ArgumentCaptor<SessionCallback> sessionCallbackCaptor =
                ArgumentCaptor.forClass(SessionCallback.class);

        @Test
        @SuppressWarnings("unchecked")
        void shouldAllocateNonceFromOnChainWhenFirstAllocation() {
            // given
            given(redisTemplate.execute(sessionCallbackCaptor.capture()))
                    .willReturn(List.of(true, 1L));

            // when
            var result = nonceManager.allocate(SOME_ADDRESS, SOME_CHAIN);

            // then
            assertThat(sessionCallbackCaptor.getValue()).isNotNull();

            var expected = NonceAllocation.builder()
                    .address(SOME_ADDRESS)
                    .chain(SOME_CHAIN)
                    .build();

            assertThat(result)
                    .usingRecursiveComparison()
                    .ignoringFields("nonce")
                    .isEqualTo(expected);
        }

        @Test
        @SuppressWarnings("unchecked")
        void shouldThrowNonceConcurrencyExceptionWhenWatchFails() {
            // given
            given(redisTemplate.execute(sessionCallbackCaptor.capture()))
                    .willReturn(null);

            // when/then
            assertThatThrownBy(() -> nonceManager.allocate(SOME_ADDRESS, SOME_CHAIN))
                    .isInstanceOf(NonceConcurrencyException.class)
                    .hasMessageContaining(SOME_ADDRESS)
                    .hasMessageContaining(SOME_CHAIN);

            assertThat(sessionCallbackCaptor.getValue()).isNotNull();
        }

        @Test
        @SuppressWarnings("unchecked")
        void shouldThrowNonceConcurrencyExceptionWhenExecReturnsEmpty() {
            // given
            given(redisTemplate.execute(sessionCallbackCaptor.capture()))
                    .willReturn(List.of());

            // when/then
            assertThatThrownBy(() -> nonceManager.allocate(SOME_ADDRESS, SOME_CHAIN))
                    .isInstanceOf(NonceConcurrencyException.class)
                    .hasMessageContaining(SOME_ADDRESS);

            assertThat(sessionCallbackCaptor.getValue()).isNotNull();
        }
    }

    @Nested
    class Release {

        @Test
        void shouldRemoveNonceFromInflightSet() {
            // given
            var allocation = someAllocation(42L);
            given(redisTemplate.opsForSet()).willReturn(setOperations);

            // when
            nonceManager.release(allocation);

            // then
            then(setOperations).should().remove(SOME_INFLIGHT_KEY, "42");
        }
    }

    @Nested
    class Confirm {

        @SuppressWarnings("rawtypes")
        private final ArgumentCaptor<RedisScript> scriptCaptor = ArgumentCaptor.forClass(RedisScript.class);

        @SuppressWarnings("rawtypes")
        private final ArgumentCaptor<List> keysCaptor = ArgumentCaptor.forClass(List.class);

        private final ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);

        @Test
        void shouldExecuteLuaScriptWithCorrectKeysAndArgs() {
            // given
            var allocation = someAllocation(10L);

            // when
            nonceManager.confirm(allocation);

            // then
            then(redisTemplate).should().execute(scriptCaptor.capture(), keysCaptor.capture(), argsCaptor.capture());
            assertThat(keysCaptor.getValue()).containsExactly(SOME_HASH_KEY, SOME_INFLIGHT_KEY);
            assertThat(argsCaptor.getValue()).containsExactly("10");
        }
    }

    @Nested
    class SyncFromChain {

        @Test
        void shouldResetAllocatedAndConfirmedToOnChainMinusOne() {
            // given
            given(onChainNonceProvider.getTransactionCount(SOME_ADDRESS, SOME_CHAIN)).willReturn(BigInteger.TEN);
            given(redisTemplate.opsForHash()).willReturn(hashOperations);

            // when
            nonceManager.syncFromChain(SOME_ADDRESS, SOME_CHAIN);

            // then
            then(hashOperations).should().put(SOME_HASH_KEY, "allocated", "9");
            then(hashOperations).should().put(SOME_HASH_KEY, "confirmed", "9");
            then(redisTemplate).should().delete(SOME_INFLIGHT_KEY);
        }

        @Test
        void shouldResetToMinusOneWhenOnChainNonceIsZero() {
            // given
            given(onChainNonceProvider.getTransactionCount(SOME_ADDRESS, SOME_CHAIN)).willReturn(BigInteger.ZERO);
            given(redisTemplate.opsForHash()).willReturn(hashOperations);

            // when
            nonceManager.syncFromChain(SOME_ADDRESS, SOME_CHAIN);

            // then
            then(hashOperations).should().put(SOME_HASH_KEY, "allocated", "-1");
            then(hashOperations).should().put(SOME_HASH_KEY, "confirmed", "-1");
            then(redisTemplate).should().delete(SOME_INFLIGHT_KEY);
        }
    }

    @Nested
    class DetectGaps {

        @Test
        void shouldReturnGapNoncesBelowOnChainCount() {
            // given
            given(onChainNonceProvider.getTransactionCount(SOME_ADDRESS, SOME_CHAIN)).willReturn(BigInteger.valueOf(10));
            given(redisTemplate.opsForSet()).willReturn(setOperations);
            given(setOperations.members(SOME_INFLIGHT_KEY)).willReturn(Set.of("5", "7", "10", "11"));

            // when
            var result = nonceManager.detectGaps(SOME_ADDRESS, SOME_CHAIN);

            // then
            assertThat(result).containsExactlyInAnyOrder(5L, 7L);
            assertThat(meterRegistry.counter(RedisNonceManager.GAPS_COUNTER_NAME).count()).isEqualTo(2.0);
        }

        @Test
        void shouldReturnEmptySetWhenNoInflightNonces() {
            // given
            given(onChainNonceProvider.getTransactionCount(SOME_ADDRESS, SOME_CHAIN)).willReturn(BigInteger.TEN);
            given(redisTemplate.opsForSet()).willReturn(setOperations);
            given(setOperations.members(SOME_INFLIGHT_KEY)).willReturn(Set.of());

            // when
            var result = nonceManager.detectGaps(SOME_ADDRESS, SOME_CHAIN);

            // then
            assertThat(result).isEmpty();
            assertThat(meterRegistry.counter(RedisNonceManager.GAPS_COUNTER_NAME).count()).isZero();
        }

        @Test
        void shouldReturnEmptySetWhenInflightMembersIsNull() {
            // given
            given(onChainNonceProvider.getTransactionCount(SOME_ADDRESS, SOME_CHAIN)).willReturn(BigInteger.TEN);
            given(redisTemplate.opsForSet()).willReturn(setOperations);
            given(setOperations.members(SOME_INFLIGHT_KEY)).willReturn(null);

            // when
            var result = nonceManager.detectGaps(SOME_ADDRESS, SOME_CHAIN);

            // then
            assertThat(result).isEmpty();
        }

        @Test
        void shouldReturnEmptySetWhenAllInflightNoncesAreAboveOnChain() {
            // given
            given(onChainNonceProvider.getTransactionCount(SOME_ADDRESS, SOME_CHAIN)).willReturn(BigInteger.valueOf(5));
            given(redisTemplate.opsForSet()).willReturn(setOperations);
            given(setOperations.members(SOME_INFLIGHT_KEY)).willReturn(Set.of("5", "6", "7"));

            // when
            var result = nonceManager.detectGaps(SOME_ADDRESS, SOME_CHAIN);

            // then
            assertThat(result).isEmpty();
        }
    }
}
