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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.stablebridge.txrecovery.domain.exception.NonceConcurrencyException;
import com.stablebridge.txrecovery.infrastructure.client.evm.EvmRpcClient;

@ExtendWith(MockitoExtension.class)
class RedisNonceManagerTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private EvmRpcClient evmRpcClient;
    @Mock private HashOperations<String, Object, Object> hashOperations;
    @Mock private SetOperations<String, String> setOperations;

    private RedisNonceManager nonceManager;

    @BeforeEach
    void setUp() {
        nonceManager = new RedisNonceManager(redisTemplate, evmRpcClient);
    }

    @Nested
    class Allocate {

        @Test
        @SuppressWarnings("unchecked")
        void shouldAllocateNonceFromOnChainWhenFirstAllocation() {
            // given
            given(redisTemplate.execute(org.mockito.ArgumentMatchers.<SessionCallback<List<Object>>>any()))
                    .willReturn(List.of(true, 1L));

            // when
            var result = nonceManager.allocate(SOME_ADDRESS, SOME_CHAIN);

            // then
            assertThat(result).isNotNull();
            assertThat(result.address()).isEqualTo(SOME_ADDRESS);
            assertThat(result.chain()).isEqualTo(SOME_CHAIN);
        }

        @Test
        @SuppressWarnings("unchecked")
        void shouldThrowNonceConcurrencyExceptionWhenWatchFails() {
            // given
            given(redisTemplate.execute(org.mockito.ArgumentMatchers.<SessionCallback<List<Object>>>any()))
                    .willReturn(null);

            // when/then
            assertThatThrownBy(() -> nonceManager.allocate(SOME_ADDRESS, SOME_CHAIN))
                    .isInstanceOf(NonceConcurrencyException.class)
                    .hasMessageContaining(SOME_ADDRESS)
                    .hasMessageContaining(SOME_CHAIN);
        }

        @Test
        @SuppressWarnings("unchecked")
        void shouldThrowNonceConcurrencyExceptionWhenExecReturnsEmpty() {
            // given
            given(redisTemplate.execute(org.mockito.ArgumentMatchers.<SessionCallback<List<Object>>>any()))
                    .willReturn(List.of());

            // when/then
            assertThatThrownBy(() -> nonceManager.allocate(SOME_ADDRESS, SOME_CHAIN))
                    .isInstanceOf(NonceConcurrencyException.class)
                    .hasMessageContaining(SOME_ADDRESS);
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

        @Test
        @SuppressWarnings("unchecked")
        void shouldUpdateConfirmedFieldAndRemoveFromInflight() {
            // given
            var allocation = someAllocation(10L);
            given(redisTemplate.execute(org.mockito.ArgumentMatchers.<SessionCallback<List<Object>>>any()))
                    .willReturn(List.of(true, 1L));

            // when
            nonceManager.confirm(allocation);

            // then
            then(redisTemplate).should().execute(org.mockito.ArgumentMatchers.<SessionCallback<List<Object>>>any());
        }

        @Test
        @SuppressWarnings("unchecked")
        void shouldUpdateConfirmedWhenNoConfirmedFieldExists() {
            // given
            var allocation = someAllocation(0L);
            given(redisTemplate.execute(org.mockito.ArgumentMatchers.<SessionCallback<List<Object>>>any()))
                    .willReturn(List.of(true, 1L));

            // when
            nonceManager.confirm(allocation);

            // then
            then(redisTemplate).should().execute(org.mockito.ArgumentMatchers.<SessionCallback<List<Object>>>any());
        }
    }

    @Nested
    class SyncFromChain {

        @Test
        void shouldResetAllocatedAndConfirmedToOnChainMinusOne() {
            // given
            given(evmRpcClient.getTransactionCount(SOME_ADDRESS, "latest")).willReturn(BigInteger.TEN);
            given(redisTemplate.opsForHash()).willReturn(hashOperations);

            // when
            nonceManager.syncFromChain(SOME_ADDRESS, SOME_CHAIN);

            // then
            then(hashOperations).should().put(SOME_HASH_KEY, "allocated", "9");
            then(hashOperations).should().put(SOME_HASH_KEY, "confirmed", "9");
            then(redisTemplate).should().delete(SOME_INFLIGHT_KEY);
        }

        @Test
        void shouldResetToZeroWhenOnChainNonceIsZero() {
            // given
            given(evmRpcClient.getTransactionCount(SOME_ADDRESS, "latest")).willReturn(BigInteger.ZERO);
            given(redisTemplate.opsForHash()).willReturn(hashOperations);

            // when
            nonceManager.syncFromChain(SOME_ADDRESS, SOME_CHAIN);

            // then
            then(hashOperations).should().put(SOME_HASH_KEY, "allocated", "0");
            then(hashOperations).should().put(SOME_HASH_KEY, "confirmed", "0");
            then(redisTemplate).should().delete(SOME_INFLIGHT_KEY);
        }
    }

    @Nested
    class DetectGaps {

        @Test
        void shouldReturnGapNoncesBelowOnChainCount() {
            // given
            given(evmRpcClient.getTransactionCount(SOME_ADDRESS, "latest")).willReturn(BigInteger.valueOf(10));
            given(redisTemplate.opsForSet()).willReturn(setOperations);
            given(setOperations.members(SOME_INFLIGHT_KEY)).willReturn(Set.of("5", "7", "10", "11"));

            // when
            var result = nonceManager.detectGaps(SOME_ADDRESS, SOME_CHAIN);

            // then
            assertThat(result).containsExactlyInAnyOrder(5L, 7L);
        }

        @Test
        void shouldReturnEmptySetWhenNoInflightNonces() {
            // given
            given(evmRpcClient.getTransactionCount(SOME_ADDRESS, "latest")).willReturn(BigInteger.TEN);
            given(redisTemplate.opsForSet()).willReturn(setOperations);
            given(setOperations.members(SOME_INFLIGHT_KEY)).willReturn(Set.of());

            // when
            var result = nonceManager.detectGaps(SOME_ADDRESS, SOME_CHAIN);

            // then
            assertThat(result).isEmpty();
        }

        @Test
        void shouldReturnEmptySetWhenInflightMembersIsNull() {
            // given
            given(evmRpcClient.getTransactionCount(SOME_ADDRESS, "latest")).willReturn(BigInteger.TEN);
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
            given(evmRpcClient.getTransactionCount(SOME_ADDRESS, "latest")).willReturn(BigInteger.valueOf(5));
            given(redisTemplate.opsForSet()).willReturn(setOperations);
            given(setOperations.members(SOME_INFLIGHT_KEY)).willReturn(Set.of("5", "6", "7"));

            // when
            var result = nonceManager.detectGaps(SOME_ADDRESS, SOME_CHAIN);

            // then
            assertThat(result).isEmpty();
        }
    }
}
