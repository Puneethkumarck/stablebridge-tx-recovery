package com.stablebridge.txrecovery.infrastructure.client.evm;

import static com.stablebridge.txrecovery.testutil.fixtures.NonceAllocationFixtures.SOME_ADDRESS;
import static com.stablebridge.txrecovery.testutil.fixtures.NonceAllocationFixtures.SOME_CHAIN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import java.math.BigInteger;
import java.util.Map;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EvmOnChainNonceProviderTest {

    @Mock
    private EvmRpcClient evmRpcClient;

    @Nested
    class GetTransactionCount {

        @Test
        void shouldDelegateToCorrectRpcClientForChain() {
            // given
            var provider = new EvmOnChainNonceProvider(Map.of(SOME_CHAIN, evmRpcClient));
            given(evmRpcClient.getTransactionCount(SOME_ADDRESS, "latest")).willReturn(BigInteger.TEN);

            // when
            var result = provider.getTransactionCount(SOME_ADDRESS, SOME_CHAIN);

            // then
            assertThat(result).isEqualTo(BigInteger.TEN);
        }

        @Test
        void shouldThrowWhenChainNotConfigured() {
            // given
            var provider = new EvmOnChainNonceProvider(Map.of());

            // when/then
            assertThatThrownBy(() -> provider.getTransactionCount(SOME_ADDRESS, "unknown_chain"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("unknown_chain");
        }

        @Test
        void shouldThrowWhenAddressIsNull() {
            // given
            var provider = new EvmOnChainNonceProvider(Map.of(SOME_CHAIN, evmRpcClient));

            // when/then
            assertThatThrownBy(() -> provider.getTransactionCount(null, SOME_CHAIN))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        void shouldThrowWhenChainIsNull() {
            // given
            var provider = new EvmOnChainNonceProvider(Map.of(SOME_CHAIN, evmRpcClient));

            // when/then
            assertThatThrownBy(() -> provider.getTransactionCount(SOME_ADDRESS, null))
                    .isInstanceOf(NullPointerException.class);
        }
    }
}
