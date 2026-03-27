package com.stablebridge.txrecovery.infrastructure.client.evm;

import static com.stablebridge.txrecovery.infrastructure.client.evm.EvmChainTransactionManagerFixtures.SOME_BROADCAST_TX_HASH;
import static com.stablebridge.txrecovery.infrastructure.client.evm.EvmChainTransactionManagerFixtures.SOME_CHAIN;
import static com.stablebridge.txrecovery.infrastructure.client.evm.EvmChainTransactionManagerFixtures.SOME_CURRENT_BLOCK_FOR_PENDING;
import static com.stablebridge.txrecovery.infrastructure.client.evm.EvmChainTransactionManagerFixtures.SOME_CURRENT_BLOCK_FOR_STUCK;
import static com.stablebridge.txrecovery.infrastructure.client.evm.EvmChainTransactionManagerFixtures.SOME_FAILED_RECEIPT;
import static com.stablebridge.txrecovery.infrastructure.client.evm.EvmChainTransactionManagerFixtures.SOME_FINALITY_BLOCKS;
import static com.stablebridge.txrecovery.infrastructure.client.evm.EvmChainTransactionManagerFixtures.SOME_LATEST_BLOCK_CONFIRMED;
import static com.stablebridge.txrecovery.infrastructure.client.evm.EvmChainTransactionManagerFixtures.SOME_LATEST_BLOCK_FINALIZED;
import static com.stablebridge.txrecovery.infrastructure.client.evm.EvmChainTransactionManagerFixtures.SOME_MEMPOOL_TX;
import static com.stablebridge.txrecovery.infrastructure.client.evm.EvmChainTransactionManagerFixtures.SOME_SIGNED_PAYLOAD_HEX;
import static com.stablebridge.txrecovery.infrastructure.client.evm.EvmChainTransactionManagerFixtures.SOME_SIGNED_TRANSACTION;
import static com.stablebridge.txrecovery.infrastructure.client.evm.EvmChainTransactionManagerFixtures.SOME_STUCK_THRESHOLD_BLOCKS;
import static com.stablebridge.txrecovery.infrastructure.client.evm.EvmChainTransactionManagerFixtures.SOME_SUCCESS_RECEIPT;
import static com.stablebridge.txrecovery.infrastructure.client.evm.EvmChainTransactionManagerFixtures.SOME_TX_HASH;
import static com.stablebridge.txrecovery.testutil.fixtures.EvmTransactionFixtures.someEvmSubmissionResource;
import static com.stablebridge.txrecovery.testutil.fixtures.EvmTransactionFixtures.someFeeEstimate;
import static com.stablebridge.txrecovery.testutil.fixtures.EvmTransactionFixtures.someTransactionIntent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.stablebridge.txrecovery.domain.transaction.model.BroadcastResult;
import com.stablebridge.txrecovery.domain.transaction.model.SignedTransaction;
import com.stablebridge.txrecovery.domain.transaction.model.TransactionStatus;
import com.stablebridge.txrecovery.domain.transaction.model.UnsignedTransaction;

@ExtendWith(MockitoExtension.class)
class EvmChainTransactionManagerTest {

    @Mock
    private EvmRpcClient rpcClient;

    @Mock
    private EvmTransactionBuilder transactionBuilder;

    private EvmChainTransactionManager manager;

    @BeforeEach
    void setUp() {
        manager = new EvmChainTransactionManager(
                rpcClient, transactionBuilder, SOME_FINALITY_BLOCKS, SOME_STUCK_THRESHOLD_BLOCKS);
    }

    @Nested
    class Build {

        @Test
        void shouldDelegateToTransactionBuilder() {
            // given
            var intent = someTransactionIntent();
            var resource = someEvmSubmissionResource();
            var expectedTx = UnsignedTransaction.builder()
                    .intentId(intent.intentId())
                    .chain(intent.chain())
                    .fromAddress(resource.fromAddress())
                    .toAddress(intent.tokenContractAddress())
                    .payload(new byte[] {0x02, 0x01})
                    .feeEstimate(someFeeEstimate())
                    .build();
            given(transactionBuilder.build(intent, resource)).willReturn(expectedTx);

            // when
            var result = manager.build(intent, resource);

            // then
            assertThat(result).usingRecursiveComparison().isEqualTo(expectedTx);
        }

        @Test
        void shouldThrowWhenResourceIsNotEvmType() {
            // given
            var intent = someTransactionIntent();
            var resource = new com.stablebridge.txrecovery.domain.transaction.model.SolanaSubmissionResource(
                    "solana", "SolAddr", "nonceAccount", "nonceAuthority");

            // when/then
            assertThatThrownBy(() -> manager.build(intent, resource))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Expected EvmSubmissionResource");
        }
    }

    @Nested
    class Broadcast {

        @Test
        void shouldReturnBroadcastResultOnSuccess() {
            // given
            given(rpcClient.getChain()).willReturn(SOME_CHAIN);
            given(rpcClient.sendRawTransaction(SOME_SIGNED_PAYLOAD_HEX)).willReturn(SOME_BROADCAST_TX_HASH);

            // when
            var result = manager.broadcast(SOME_SIGNED_TRANSACTION, SOME_CHAIN);

            // then
            var expected = BroadcastResult.builder()
                    .txHash(SOME_BROADCAST_TX_HASH)
                    .chain(SOME_CHAIN)
                    .broadcastedAt(Instant.now())
                    .build();
            assertThat(result)
                    .usingRecursiveComparison()
                    .ignoringFields("broadcastedAt")
                    .isEqualTo(expected);
        }

        @Test
        void shouldHandleAlreadyKnownAsSuccess() {
            // given
            given(rpcClient.getChain()).willReturn(SOME_CHAIN);
            given(rpcClient.sendRawTransaction(SOME_SIGNED_PAYLOAD_HEX))
                    .willThrow(new EvmRpcException("JSON-RPC error [-32000]: already known", false));

            // when
            var result = manager.broadcast(SOME_SIGNED_TRANSACTION, SOME_CHAIN);

            // then
            var expected = BroadcastResult.builder()
                    .txHash("placeholder")
                    .chain(SOME_CHAIN)
                    .broadcastedAt(Instant.now())
                    .details(Map.of("note", "Transaction already known by node"))
                    .build();
            assertThat(result)
                    .usingRecursiveComparison()
                    .ignoringFields("broadcastedAt", "txHash")
                    .isEqualTo(expected);
            assertThat(result.txHash()).startsWith("0x");
        }

        @Test
        void shouldThrowNonRetryableForNonceTooLow() {
            // given
            given(rpcClient.getChain()).willReturn(SOME_CHAIN);
            given(rpcClient.sendRawTransaction(SOME_SIGNED_PAYLOAD_HEX))
                    .willThrow(new EvmRpcException("JSON-RPC error [-32000]: nonce too low", false));

            // when/then
            assertThatThrownBy(() -> manager.broadcast(SOME_SIGNED_TRANSACTION, SOME_CHAIN))
                    .isInstanceOf(EvmRpcException.class)
                    .hasMessageContaining("Nonce too low");
        }

        @Test
        void shouldPropagateTransientErrors() {
            // given
            given(rpcClient.getChain()).willReturn(SOME_CHAIN);
            given(rpcClient.sendRawTransaction(SOME_SIGNED_PAYLOAD_HEX))
                    .willThrow(new EvmRpcException("Connection timeout"));

            // when/then
            assertThatThrownBy(() -> manager.broadcast(SOME_SIGNED_TRANSACTION, SOME_CHAIN))
                    .isInstanceOf(EvmRpcException.class)
                    .hasMessageContaining("Connection timeout");
        }

        @Test
        void shouldThrowWhenChainMismatch() {
            // given
            var polygonSignedTx = SignedTransaction.builder()
                    .intentId("intent-001")
                    .chain("polygon")
                    .signedPayload(new byte[] {0x01})
                    .signerAddress("0x1111111111111111111111111111111111111111")
                    .build();
            given(rpcClient.getChain()).willReturn(SOME_CHAIN);

            // when/then
            assertThatThrownBy(() -> manager.broadcast(polygonSignedTx, "polygon"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Manager for chain ethereum cannot serve chain polygon");
        }
    }

    @Nested
    class CheckStatus {

        @Nested
        class WhenReceiptPresent {

            @Test
            void shouldReturnConfirmedWhenBelowFinalityDepth() {
                // given
                given(rpcClient.getChain()).willReturn(SOME_CHAIN);
                given(rpcClient.getTransactionReceipt(SOME_TX_HASH))
                        .willReturn(Optional.of(SOME_SUCCESS_RECEIPT));
                given(rpcClient.getBlockByNumber("latest", false))
                        .willReturn(SOME_LATEST_BLOCK_CONFIRMED);

                // when
                var result = manager.checkStatus(SOME_TX_HASH, SOME_CHAIN);

                // then
                assertThat(result).isEqualTo(TransactionStatus.CONFIRMED);
            }

            @Test
            void shouldReturnFinalizedWhenAtFinalityDepth() {
                // given
                given(rpcClient.getChain()).willReturn(SOME_CHAIN);
                given(rpcClient.getTransactionReceipt(SOME_TX_HASH))
                        .willReturn(Optional.of(SOME_SUCCESS_RECEIPT));
                given(rpcClient.getBlockByNumber("latest", false))
                        .willReturn(SOME_LATEST_BLOCK_FINALIZED);

                // when
                var result = manager.checkStatus(SOME_TX_HASH, SOME_CHAIN);

                // then
                assertThat(result).isEqualTo(TransactionStatus.FINALIZED);
            }

            @Test
            void shouldReturnFailedWhenReceiptStatusIsZero() {
                // given
                given(rpcClient.getChain()).willReturn(SOME_CHAIN);
                given(rpcClient.getTransactionReceipt(SOME_TX_HASH))
                        .willReturn(Optional.of(SOME_FAILED_RECEIPT));

                // when
                var result = manager.checkStatus(SOME_TX_HASH, SOME_CHAIN);

                // then
                assertThat(result).isEqualTo(TransactionStatus.FAILED);
            }
        }

        @Nested
        class WhenNoReceipt {

            @Test
            void shouldReturnPendingWhenInMempool() {
                // given
                given(rpcClient.getChain()).willReturn(SOME_CHAIN);
                given(rpcClient.getTransactionReceipt(SOME_TX_HASH))
                        .willReturn(Optional.empty());
                given(rpcClient.getTransactionByHash(SOME_TX_HASH))
                        .willReturn(Optional.of(SOME_MEMPOOL_TX));
                given(rpcClient.getBlockByNumber("latest", false))
                        .willReturn(SOME_CURRENT_BLOCK_FOR_PENDING);

                // when
                var result = manager.checkStatus(SOME_TX_HASH, SOME_CHAIN);

                // then
                assertThat(result).isEqualTo(TransactionStatus.PENDING);
            }

            @Test
            void shouldReturnDroppedWhenNotInMempool() {
                // given
                given(rpcClient.getChain()).willReturn(SOME_CHAIN);
                given(rpcClient.getTransactionReceipt(SOME_TX_HASH))
                        .willReturn(Optional.empty());
                given(rpcClient.getTransactionByHash(SOME_TX_HASH))
                        .willReturn(Optional.empty());

                // when
                var result = manager.checkStatus(SOME_TX_HASH, SOME_CHAIN);

                // then
                assertThat(result).isEqualTo(TransactionStatus.DROPPED);
            }

            @Test
            void shouldReturnStuckWhenPendingBeyondThreshold() {
                // given
                given(rpcClient.getChain()).willReturn(SOME_CHAIN);
                given(rpcClient.getTransactionReceipt(SOME_TX_HASH))
                        .willReturn(Optional.empty());
                given(rpcClient.getTransactionByHash(SOME_TX_HASH))
                        .willReturn(Optional.of(SOME_MEMPOOL_TX));
                given(rpcClient.getBlockByNumber("latest", false))
                        .willReturn(SOME_CURRENT_BLOCK_FOR_PENDING);
                manager.checkStatus(SOME_TX_HASH, SOME_CHAIN);

                given(rpcClient.getBlockByNumber("latest", false))
                        .willReturn(SOME_CURRENT_BLOCK_FOR_STUCK);

                // when
                var result = manager.checkStatus(SOME_TX_HASH, SOME_CHAIN);

                // then
                assertThat(result).isEqualTo(TransactionStatus.STUCK);
            }
        }

        @Nested
        class ChainReorg {

            @Test
            void shouldReturnPendingWhenConfirmedTxDisappears() {
                // given
                given(rpcClient.getChain()).willReturn(SOME_CHAIN);
                given(rpcClient.getTransactionReceipt(SOME_TX_HASH))
                        .willReturn(Optional.of(SOME_SUCCESS_RECEIPT));
                given(rpcClient.getBlockByNumber("latest", false))
                        .willReturn(SOME_LATEST_BLOCK_CONFIRMED);
                manager.checkStatus(SOME_TX_HASH, SOME_CHAIN);

                given(rpcClient.getTransactionReceipt(SOME_TX_HASH))
                        .willReturn(Optional.empty());
                given(rpcClient.getTransactionByHash(SOME_TX_HASH))
                        .willReturn(Optional.of(SOME_MEMPOOL_TX));
                given(rpcClient.getBlockByNumber("latest", false))
                        .willReturn(SOME_CURRENT_BLOCK_FOR_PENDING);

                // when
                var result = manager.checkStatus(SOME_TX_HASH, SOME_CHAIN);

                // then
                assertThat(result).isEqualTo(TransactionStatus.PENDING);
            }
        }

        @Test
        void shouldThrowWhenChainMismatch() {
            // given
            given(rpcClient.getChain()).willReturn(SOME_CHAIN);

            // when/then
            assertThatThrownBy(() -> manager.checkStatus(SOME_TX_HASH, "polygon"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Manager for chain ethereum cannot serve chain polygon");
        }
    }
}
