package com.stablebridge.txrecovery.infrastructure.client.solana;

import static com.stablebridge.txrecovery.testutil.fixtures.SolanaChainTransactionManagerFixtures.SOME_BROADCAST_TX_HASH;
import static com.stablebridge.txrecovery.testutil.fixtures.SolanaChainTransactionManagerFixtures.SOME_CHAIN;
import static com.stablebridge.txrecovery.testutil.fixtures.SolanaChainTransactionManagerFixtures.SOME_SIGNED_PAYLOAD;
import static com.stablebridge.txrecovery.testutil.fixtures.SolanaChainTransactionManagerFixtures.SOME_SIGNED_TRANSACTION;
import static com.stablebridge.txrecovery.testutil.fixtures.SolanaChainTransactionManagerFixtures.SOME_STUCK_THRESHOLD_SECONDS;
import static com.stablebridge.txrecovery.testutil.fixtures.SolanaChainTransactionManagerFixtures.SOME_TX_HASH;
import static com.stablebridge.txrecovery.testutil.fixtures.SolanaTransactionFixtures.someSolanaFeeEstimate;
import static com.stablebridge.txrecovery.testutil.fixtures.SolanaTransactionFixtures.someSolanaSubmissionResource;
import static com.stablebridge.txrecovery.testutil.fixtures.SolanaTransactionFixtures.someSolanaTransactionIntent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.stablebridge.txrecovery.domain.address.model.AddressTier;
import com.stablebridge.txrecovery.domain.transaction.model.BroadcastResult;
import com.stablebridge.txrecovery.domain.transaction.model.EvmSubmissionResource;
import com.stablebridge.txrecovery.domain.transaction.model.SignedTransaction;
import com.stablebridge.txrecovery.domain.transaction.model.TransactionStatus;
import com.stablebridge.txrecovery.domain.transaction.model.UnsignedTransaction;

@ExtendWith(MockitoExtension.class)
class SolanaChainTransactionManagerTest {

    private static final SolanaSignatureStatus FINALIZED_STATUS = SolanaSignatureStatus.builder()
            .slot(100L)
            .confirmations(null)
            .confirmationStatus("finalized")
            .err(null)
            .build();

    private static final SolanaSignatureStatus CONFIRMED_STATUS = SolanaSignatureStatus.builder()
            .slot(100L)
            .confirmations(10L)
            .confirmationStatus("confirmed")
            .err(null)
            .build();

    private static final SolanaSignatureStatus FAILED_STATUS = SolanaSignatureStatus.builder()
            .slot(100L)
            .confirmations(null)
            .confirmationStatus("finalized")
            .err("InstructionError")
            .build();

    private static final SolanaSignatureStatus PROCESSED_STATUS = SolanaSignatureStatus.builder()
            .slot(100L)
            .confirmations(0L)
            .confirmationStatus("processed")
            .err(null)
            .build();

    @Mock
    private SolanaRpcClient rpcClient;

    @Mock
    private SolanaTransactionBuilder transactionBuilder;

    private SolanaChainTransactionManager manager;

    @BeforeEach
    void setUp() {
        manager = new SolanaChainTransactionManager(
                rpcClient, transactionBuilder, SOME_CHAIN, SOME_STUCK_THRESHOLD_SECONDS);
    }

    @Nested
    class Build {

        @Test
        void shouldDelegateToTransactionBuilder() {
            // given
            var intent = someSolanaTransactionIntent();
            var resource = someSolanaSubmissionResource();
            var expectedTx = UnsignedTransaction.builder()
                    .intentId(intent.intentId())
                    .chain(intent.chain())
                    .fromAddress(resource.fromAddress())
                    .toAddress(intent.toAddress())
                    .payload(new byte[] {0x02, 0x01})
                    .feeEstimate(someSolanaFeeEstimate())
                    .build();
            given(transactionBuilder.build(intent, resource)).willReturn(expectedTx);

            // when
            var result = manager.build(intent, resource);

            // then
            assertThat(result).usingRecursiveComparison().isEqualTo(expectedTx);
        }

        @Test
        void shouldThrowWhenResourceIsNotSolanaType() {
            // given
            var intent = someSolanaTransactionIntent();
            var resource = new EvmSubmissionResource(
                    "ethereum", "0x1111111111111111111111111111111111111111", 1L, AddressTier.HOT);

            // when/then
            assertThatThrownBy(() -> manager.build(intent, resource))
                    .isInstanceOf(SolanaRpcException.class)
                    .hasMessageContaining("Expected SolanaSubmissionResource");
        }
    }

    @Nested
    class Broadcast {

        @Test
        void shouldReturnBroadcastResultOnSuccess() {
            // given
            given(rpcClient.sendTransaction(SOME_SIGNED_PAYLOAD)).willReturn(SOME_BROADCAST_TX_HASH);

            // when
            var result = manager.broadcast(SOME_SIGNED_TRANSACTION, SOME_CHAIN);

            // then
            var expected = BroadcastResult.builder()
                    .txHash(SOME_BROADCAST_TX_HASH)
                    .chain(SOME_CHAIN)
                    .broadcastedAt(result.broadcastedAt())
                    .build();
            assertThat(result)
                    .usingRecursiveComparison()
                    .ignoringFields("broadcastedAt")
                    .isEqualTo(expected);
        }

        @Test
        void shouldThrowWhenChainMismatch() {
            // given
            var stellarSignedTx = SignedTransaction.builder()
                    .intentId("intent-001")
                    .chain("stellar")
                    .signedPayload(new byte[] {0x01})
                    .signerAddress("GABCD")
                    .build();

            // when/then
            assertThatThrownBy(() -> manager.broadcast(stellarSignedTx, "stellar"))
                    .isInstanceOf(SolanaRpcException.class)
                    .hasMessageContaining("Manager for chain solana cannot serve chain stellar");
        }
    }

    @Nested
    class CheckStatus {

        @Nested
        class WhenStatusFound {

            @Test
            void shouldReturnFinalizedWhenCommitmentIsFinalized() {
                // given
                given(rpcClient.getSignatureStatuses(List.of(SOME_TX_HASH)))
                        .willReturn(List.of(FINALIZED_STATUS));

                // when
                var result = manager.checkStatus(SOME_TX_HASH, SOME_CHAIN);

                // then
                assertThat(result).isEqualTo(TransactionStatus.FINALIZED);
            }

            @Test
            void shouldReturnConfirmedWhenCommitmentIsConfirmed() {
                // given
                given(rpcClient.getSignatureStatuses(List.of(SOME_TX_HASH)))
                        .willReturn(List.of(CONFIRMED_STATUS));

                // when
                var result = manager.checkStatus(SOME_TX_HASH, SOME_CHAIN);

                // then
                assertThat(result).isEqualTo(TransactionStatus.CONFIRMED);
            }

            @Test
            void shouldReturnFailedWhenStatusHasError() {
                // given
                given(rpcClient.getSignatureStatuses(List.of(SOME_TX_HASH)))
                        .willReturn(List.of(FAILED_STATUS));

                // when
                var result = manager.checkStatus(SOME_TX_HASH, SOME_CHAIN);

                // then
                assertThat(result).isEqualTo(TransactionStatus.FAILED);
            }
        }

        @Nested
        class WhenStatusNotFound {

            @Test
            void shouldReturnDroppedWhenSignatureIsNull() {
                // given
                var nullList = new ArrayList<SolanaSignatureStatus>();
                nullList.add(null);
                given(rpcClient.getSignatureStatuses(List.of(SOME_TX_HASH)))
                        .willReturn(Collections.unmodifiableList(nullList));

                // when
                var result = manager.checkStatus(SOME_TX_HASH, SOME_CHAIN);

                // then
                assertThat(result).isEqualTo(TransactionStatus.DROPPED);
            }

            @Test
            void shouldReturnDroppedWhenStatusListIsEmpty() {
                // given
                given(rpcClient.getSignatureStatuses(List.of(SOME_TX_HASH)))
                        .willReturn(List.of());

                // when
                var result = manager.checkStatus(SOME_TX_HASH, SOME_CHAIN);

                // then
                assertThat(result).isEqualTo(TransactionStatus.DROPPED);
            }
        }

        @Nested
        class TimeBasedStuckDetection {

            @Test
            void shouldReturnPendingOnFirstCheck() {
                // given
                given(rpcClient.getSignatureStatuses(List.of(SOME_TX_HASH)))
                        .willReturn(List.of(PROCESSED_STATUS));

                // when
                var result = manager.checkStatus(SOME_TX_HASH, SOME_CHAIN);

                // then
                assertThat(result).isEqualTo(TransactionStatus.PENDING);
            }

            @Test
            @SuppressWarnings("java:S2925")
            void shouldReturnStuckWhenThresholdExceeded() throws InterruptedException {
                // given
                var stuckManager = new SolanaChainTransactionManager(
                        rpcClient, transactionBuilder, SOME_CHAIN, 0L);
                given(rpcClient.getSignatureStatuses(List.of(SOME_TX_HASH)))
                        .willReturn(List.of(PROCESSED_STATUS));
                stuckManager.checkStatus(SOME_TX_HASH, SOME_CHAIN);
                Thread.sleep(1_100);

                // when
                var result = stuckManager.checkStatus(SOME_TX_HASH, SOME_CHAIN);

                // then
                assertThat(result).isEqualTo(TransactionStatus.STUCK);
            }
        }

        @Test
        void shouldThrowWhenChainMismatch() {
            // when/then
            assertThatThrownBy(() -> manager.checkStatus(SOME_TX_HASH, "stellar"))
                    .isInstanceOf(SolanaRpcException.class)
                    .hasMessageContaining("Manager for chain solana cannot serve chain stellar");
        }
    }
}
