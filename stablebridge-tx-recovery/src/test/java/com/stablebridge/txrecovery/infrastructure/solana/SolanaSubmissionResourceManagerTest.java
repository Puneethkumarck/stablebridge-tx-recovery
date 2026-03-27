package com.stablebridge.txrecovery.infrastructure.solana;

import static com.stablebridge.txrecovery.testutil.fixtures.SolanaNonceAccountFixtures.SOME_AVAILABLE_NONCE_ACCOUNT;
import static com.stablebridge.txrecovery.testutil.fixtures.SolanaNonceAccountFixtures.SOME_CHAIN;
import static com.stablebridge.txrecovery.testutil.fixtures.SolanaNonceAccountFixtures.SOME_NEW_NONCE_VALUE;
import static com.stablebridge.txrecovery.testutil.fixtures.SolanaNonceAccountFixtures.SOME_NONCE_ACCOUNT_ADDRESS;
import static com.stablebridge.txrecovery.testutil.fixtures.SolanaNonceAccountFixtures.SOME_NONCE_VALUE;
import static com.stablebridge.txrecovery.testutil.fixtures.TransactionIntentFixtures.SOME_SOLANA_SEQUENTIAL_INTENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.stablebridge.txrecovery.domain.address.port.NonceAccountPoolRepository;
import com.stablebridge.txrecovery.domain.address.port.PoolExhaustedAlertPublisher;
import com.stablebridge.txrecovery.domain.exception.NoAvailableAddressException;
import com.stablebridge.txrecovery.domain.transaction.model.SolanaSubmissionResource;
import com.stablebridge.txrecovery.infrastructure.client.solana.SolanaCommitment;
import com.stablebridge.txrecovery.infrastructure.client.solana.SolanaRpcClient;

@ExtendWith(MockitoExtension.class)
class SolanaSubmissionResourceManagerTest {

    private static final int MIN_AVAILABLE = 3;

    @Mock
    private NonceAccountPoolRepository nonceAccountPoolRepository;

    @Mock
    private SolanaRpcClient rpcClient;

    @Mock
    private PoolExhaustedAlertPublisher poolExhaustedAlertPublisher;

    private SolanaSubmissionResourceManager resourceManager;

    @BeforeEach
    void setUp() {
        resourceManager = new SolanaSubmissionResourceManager(
                nonceAccountPoolRepository, rpcClient, poolExhaustedAlertPublisher, MIN_AVAILABLE);
    }

    @Nested
    class Acquire {

        @Test
        void shouldAcquireNonceAccountAndReadNonceFromChain() {
            // given
            var intent = SOME_SOLANA_SEQUENTIAL_INTENT;
            var nonceAccount = SOME_AVAILABLE_NONCE_ACCOUNT;

            given(nonceAccountPoolRepository.findAvailableByChain(SOME_CHAIN))
                    .willReturn(Optional.of(nonceAccount));
            given(rpcClient.getNonce(SOME_NONCE_ACCOUNT_ADDRESS, SolanaCommitment.CONFIRMED))
                    .willReturn(SOME_NONCE_VALUE);
            given(nonceAccountPoolRepository.countAvailableByChain(SOME_CHAIN))
                    .willReturn((long) MIN_AVAILABLE);

            // when
            var result = resourceManager.acquire(intent);

            // then
            var expected = SolanaSubmissionResource.builder()
                    .chain(SOME_CHAIN)
                    .fromAddress(nonceAccount.authorityAddress())
                    .nonceAccountAddress(SOME_NONCE_ACCOUNT_ADDRESS)
                    .nonceValue(SOME_NONCE_VALUE)
                    .build();

            assertThat(result)
                    .usingRecursiveComparison()
                    .isEqualTo(expected);

            then(nonceAccountPoolRepository).should()
                    .markInUse(SOME_NONCE_ACCOUNT_ADDRESS, SOME_CHAIN, intent.intentId());
        }

        @Test
        void shouldThrowAndPublishAlertWhenPoolExhausted() {
            // given
            var intent = SOME_SOLANA_SEQUENTIAL_INTENT;

            given(nonceAccountPoolRepository.findAvailableByChain(SOME_CHAIN))
                    .willReturn(Optional.empty());

            // when / then
            assertThatThrownBy(() -> resourceManager.acquire(intent))
                    .isInstanceOf(NoAvailableAddressException.class)
                    .hasMessageContaining(SOME_CHAIN)
                    .hasMessageContaining("NONCE_ACCOUNT");

            then(poolExhaustedAlertPublisher).should().publish(SOME_CHAIN, "NONCE_ACCOUNT");
        }

        @Test
        void shouldRollbackToAvailableWhenNonceReadFails() {
            // given
            var intent = SOME_SOLANA_SEQUENTIAL_INTENT;
            var nonceAccount = SOME_AVAILABLE_NONCE_ACCOUNT;

            given(nonceAccountPoolRepository.findAvailableByChain(SOME_CHAIN))
                    .willReturn(Optional.of(nonceAccount));
            willThrow(new RuntimeException("RPC unavailable"))
                    .given(rpcClient).getNonce(SOME_NONCE_ACCOUNT_ADDRESS, SolanaCommitment.CONFIRMED);

            // when / then
            assertThatThrownBy(() -> resourceManager.acquire(intent))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("RPC unavailable");

            then(nonceAccountPoolRepository).should()
                    .markAvailable(SOME_NONCE_ACCOUNT_ADDRESS, SOME_CHAIN);
        }

        @Test
        void shouldPublishLowPoolAlertWhenBelowThreshold() {
            // given
            var intent = SOME_SOLANA_SEQUENTIAL_INTENT;
            var nonceAccount = SOME_AVAILABLE_NONCE_ACCOUNT;

            given(nonceAccountPoolRepository.findAvailableByChain(SOME_CHAIN))
                    .willReturn(Optional.of(nonceAccount));
            given(rpcClient.getNonce(SOME_NONCE_ACCOUNT_ADDRESS, SolanaCommitment.CONFIRMED))
                    .willReturn(SOME_NONCE_VALUE);
            given(nonceAccountPoolRepository.countAvailableByChain(SOME_CHAIN))
                    .willReturn((long) MIN_AVAILABLE - 1);

            // when
            resourceManager.acquire(intent);

            // then
            then(poolExhaustedAlertPublisher).should().publish(SOME_CHAIN, "NONCE_ACCOUNT");
        }
    }

    @Nested
    class Release {

        @Test
        void shouldMarkAvailableWithoutNonceAdvance() {
            // given
            var resource = SolanaSubmissionResource.builder()
                    .chain(SOME_CHAIN)
                    .fromAddress(SOME_AVAILABLE_NONCE_ACCOUNT.authorityAddress())
                    .nonceAccountAddress(SOME_NONCE_ACCOUNT_ADDRESS)
                    .nonceValue(SOME_NONCE_VALUE)
                    .build();

            // when
            resourceManager.release(resource);

            // then
            then(nonceAccountPoolRepository).should()
                    .markAvailable(SOME_NONCE_ACCOUNT_ADDRESS, SOME_CHAIN);
            then(rpcClient).shouldHaveNoInteractions();
        }
    }

    @Nested
    class Consume {

        @Test
        void shouldReadNewNonceAndMarkAvailable() {
            // given
            var resource = SolanaSubmissionResource.builder()
                    .chain(SOME_CHAIN)
                    .fromAddress(SOME_AVAILABLE_NONCE_ACCOUNT.authorityAddress())
                    .nonceAccountAddress(SOME_NONCE_ACCOUNT_ADDRESS)
                    .nonceValue(SOME_NONCE_VALUE)
                    .build();

            given(rpcClient.getNonce(SOME_NONCE_ACCOUNT_ADDRESS, SolanaCommitment.CONFIRMED))
                    .willReturn(SOME_NEW_NONCE_VALUE);

            // when
            resourceManager.consume(resource);

            // then
            then(nonceAccountPoolRepository).should()
                    .updateNonceValue(SOME_NONCE_ACCOUNT_ADDRESS, SOME_CHAIN, SOME_NEW_NONCE_VALUE);
            then(nonceAccountPoolRepository).should()
                    .markAvailable(SOME_NONCE_ACCOUNT_ADDRESS, SOME_CHAIN);
        }
    }
}
