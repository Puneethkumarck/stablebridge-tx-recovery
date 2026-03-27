package com.stablebridge.txrecovery.infrastructure.redis;

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

import com.stablebridge.txrecovery.domain.address.model.NonceAllocation;
import com.stablebridge.txrecovery.domain.exception.NonceConcurrencyException;
import com.stablebridge.txrecovery.infrastructure.client.evm.EvmRpcClient;

@ExtendWith(MockitoExtension.class)
class RedisNonceManagerTest {

    private static final String ADDRESS = "0x742d35Cc6634C0532925a3b844Bc9e7595f2bD28";
    private static final String CHAIN = "ethereum_mainnet";
    private static final String HASH_KEY = "str:nonce:ethereum_mainnet:0x742d35Cc6634C0532925a3b844Bc9e7595f2bD28";
    private static final String INFLIGHT_KEY =
            "str:nonce:inflight:ethereum_mainnet:0x742d35Cc6634C0532925a3b844Bc9e7595f2bD28";

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
            given(redisTemplate.opsForHash()).willReturn(hashOperations);
            given(hashOperations.get(HASH_KEY, "allocated")).willReturn("5");

            // when
            var result = nonceManager.allocate(ADDRESS, CHAIN);

            // then
            var expected = NonceAllocation.builder().address(ADDRESS).chain(CHAIN).nonce(5L).build();
            assertThat(result).usingRecursiveComparison().isEqualTo(expected);
        }

        @Test
        @SuppressWarnings("unchecked")
        void shouldThrowNonceConcurrencyExceptionWhenWatchFails() {
            // given
            given(redisTemplate.execute(org.mockito.ArgumentMatchers.<SessionCallback<List<Object>>>any()))
                    .willReturn(null);

            // when/then
            assertThatThrownBy(() -> nonceManager.allocate(ADDRESS, CHAIN))
                    .isInstanceOf(NonceConcurrencyException.class)
                    .hasMessageContaining(ADDRESS)
                    .hasMessageContaining(CHAIN);
        }

        @Test
        @SuppressWarnings("unchecked")
        void shouldThrowNonceConcurrencyExceptionWhenExecReturnsEmpty() {
            // given
            given(redisTemplate.execute(org.mockito.ArgumentMatchers.<SessionCallback<List<Object>>>any()))
                    .willReturn(List.of());

            // when/then
            assertThatThrownBy(() -> nonceManager.allocate(ADDRESS, CHAIN))
                    .isInstanceOf(NonceConcurrencyException.class)
                    .hasMessageContaining(ADDRESS);
        }
    }

    @Nested
    class Release {

        @Test
        void shouldRemoveNonceFromInflightSet() {
            // given
            var allocation = NonceAllocation.builder().address(ADDRESS).chain(CHAIN).nonce(42L).build();
            given(redisTemplate.opsForSet()).willReturn(setOperations);

            // when
            nonceManager.release(allocation);

            // then
            then(setOperations).should().remove(INFLIGHT_KEY, "42");
        }
    }

    @Nested
    class Confirm {

        @Test
        void shouldUpdateConfirmedFieldAndRemoveFromInflight() {
            // given
            var allocation = NonceAllocation.builder().address(ADDRESS).chain(CHAIN).nonce(10L).build();
            given(redisTemplate.opsForHash()).willReturn(hashOperations);
            given(hashOperations.get(HASH_KEY, "confirmed")).willReturn("5");
            given(redisTemplate.opsForSet()).willReturn(setOperations);

            // when
            nonceManager.confirm(allocation);

            // then
            then(hashOperations).should().put(HASH_KEY, "confirmed", "10");
            then(setOperations).should().remove(INFLIGHT_KEY, "10");
        }

        @Test
        void shouldNotUpdateConfirmedWhenNonceIsLowerThanCurrent() {
            // given
            var allocation = NonceAllocation.builder().address(ADDRESS).chain(CHAIN).nonce(3L).build();
            given(redisTemplate.opsForHash()).willReturn(hashOperations);
            given(hashOperations.get(HASH_KEY, "confirmed")).willReturn("5");
            given(redisTemplate.opsForSet()).willReturn(setOperations);

            // when
            nonceManager.confirm(allocation);

            // then
            then(hashOperations).should().get(HASH_KEY, "confirmed");
            then(setOperations).should().remove(INFLIGHT_KEY, "3");
        }

        @Test
        void shouldUpdateConfirmedWhenNoConfirmedFieldExists() {
            // given
            var allocation = NonceAllocation.builder().address(ADDRESS).chain(CHAIN).nonce(0L).build();
            given(redisTemplate.opsForHash()).willReturn(hashOperations);
            given(hashOperations.get(HASH_KEY, "confirmed")).willReturn(null);
            given(redisTemplate.opsForSet()).willReturn(setOperations);

            // when
            nonceManager.confirm(allocation);

            // then
            then(hashOperations).should().put(HASH_KEY, "confirmed", "0");
            then(setOperations).should().remove(INFLIGHT_KEY, "0");
        }
    }

    @Nested
    class SyncFromChain {

        @Test
        void shouldResetAllocatedAndConfirmedToOnChainMinusOne() {
            // given
            given(evmRpcClient.getTransactionCount(ADDRESS, "latest")).willReturn(BigInteger.TEN);
            given(redisTemplate.opsForHash()).willReturn(hashOperations);

            // when
            nonceManager.syncFromChain(ADDRESS, CHAIN);

            // then
            then(hashOperations).should().put(HASH_KEY, "allocated", "9");
            then(hashOperations).should().put(HASH_KEY, "confirmed", "9");
            then(redisTemplate).should().delete(INFLIGHT_KEY);
        }

        @Test
        void shouldResetToZeroWhenOnChainNonceIsZero() {
            // given
            given(evmRpcClient.getTransactionCount(ADDRESS, "latest")).willReturn(BigInteger.ZERO);
            given(redisTemplate.opsForHash()).willReturn(hashOperations);

            // when
            nonceManager.syncFromChain(ADDRESS, CHAIN);

            // then
            then(hashOperations).should().put(HASH_KEY, "allocated", "0");
            then(hashOperations).should().put(HASH_KEY, "confirmed", "0");
            then(redisTemplate).should().delete(INFLIGHT_KEY);
        }
    }

    @Nested
    class DetectGaps {

        @Test
        void shouldReturnGapNoncesBelowOnChainCount() {
            // given
            given(evmRpcClient.getTransactionCount(ADDRESS, "latest")).willReturn(BigInteger.valueOf(10));
            given(redisTemplate.opsForSet()).willReturn(setOperations);
            given(setOperations.members(INFLIGHT_KEY)).willReturn(Set.of("5", "7", "10", "11"));

            // when
            var result = nonceManager.detectGaps(ADDRESS, CHAIN);

            // then
            assertThat(result).containsExactlyInAnyOrder(5L, 7L);
        }

        @Test
        void shouldReturnEmptySetWhenNoInflightNonces() {
            // given
            given(evmRpcClient.getTransactionCount(ADDRESS, "latest")).willReturn(BigInteger.TEN);
            given(redisTemplate.opsForSet()).willReturn(setOperations);
            given(setOperations.members(INFLIGHT_KEY)).willReturn(Set.of());

            // when
            var result = nonceManager.detectGaps(ADDRESS, CHAIN);

            // then
            assertThat(result).isEmpty();
        }

        @Test
        void shouldReturnEmptySetWhenInflightMembersIsNull() {
            // given
            given(evmRpcClient.getTransactionCount(ADDRESS, "latest")).willReturn(BigInteger.TEN);
            given(redisTemplate.opsForSet()).willReturn(setOperations);
            given(setOperations.members(INFLIGHT_KEY)).willReturn(null);

            // when
            var result = nonceManager.detectGaps(ADDRESS, CHAIN);

            // then
            assertThat(result).isEmpty();
        }

        @Test
        void shouldReturnEmptySetWhenAllInflightNoncesAreAboveOnChain() {
            // given
            given(evmRpcClient.getTransactionCount(ADDRESS, "latest")).willReturn(BigInteger.valueOf(5));
            given(redisTemplate.opsForSet()).willReturn(setOperations);
            given(setOperations.members(INFLIGHT_KEY)).willReturn(Set.of("5", "6", "7"));

            // when
            var result = nonceManager.detectGaps(ADDRESS, CHAIN);

            // then
            assertThat(result).isEmpty();
        }
    }
}
