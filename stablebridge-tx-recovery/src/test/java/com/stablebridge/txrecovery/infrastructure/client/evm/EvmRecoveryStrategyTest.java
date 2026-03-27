package com.stablebridge.txrecovery.infrastructure.client.evm;

import static com.stablebridge.txrecovery.infrastructure.client.evm.EvmRecoveryFixtures.SOME_CHAIN;
import static com.stablebridge.txrecovery.infrastructure.client.evm.EvmRecoveryFixtures.SOME_CHAIN_ID;
import static com.stablebridge.txrecovery.infrastructure.client.evm.EvmRecoveryFixtures.SOME_FAST_FEE;
import static com.stablebridge.txrecovery.infrastructure.client.evm.EvmRecoveryFixtures.SOME_FROM_ADDRESS;
import static com.stablebridge.txrecovery.infrastructure.client.evm.EvmRecoveryFixtures.SOME_LOW_FAST_FEE;
import static com.stablebridge.txrecovery.infrastructure.client.evm.EvmRecoveryFixtures.SOME_LOW_FEE_MEMPOOL_TRANSACTION;
import static com.stablebridge.txrecovery.infrastructure.client.evm.EvmRecoveryFixtures.SOME_MEMPOOL_TRANSACTION;
import static com.stablebridge.txrecovery.infrastructure.client.evm.EvmRecoveryFixtures.SOME_RECEIPT;
import static com.stablebridge.txrecovery.infrastructure.client.evm.EvmRecoveryFixtures.SOME_REPLACEMENT_FEE;
import static com.stablebridge.txrecovery.infrastructure.client.evm.EvmRecoveryFixtures.SOME_REPLACEMENT_TX_HASH;
import static com.stablebridge.txrecovery.infrastructure.client.evm.EvmRecoveryFixtures.SOME_TX_HASH;
import static com.stablebridge.txrecovery.infrastructure.client.evm.EvmRecoveryFixtures.SOME_URGENT_FEE;
import static com.stablebridge.txrecovery.infrastructure.client.evm.EvmRecoveryFixtures.someStuckTransaction;
import static com.stablebridge.txrecovery.testutil.TestUtils.eqIgnoring;
import static com.stablebridge.txrecovery.testutil.TestUtils.eqIgnoringTimestamps;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.math.BigInteger;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.stablebridge.txrecovery.domain.address.model.ChainFamily;
import com.stablebridge.txrecovery.domain.recovery.model.FeeUrgency;
import com.stablebridge.txrecovery.domain.recovery.model.RecoveryOutcome;
import com.stablebridge.txrecovery.domain.recovery.model.RecoveryPlan;
import com.stablebridge.txrecovery.domain.recovery.model.RecoveryResult;
import com.stablebridge.txrecovery.domain.recovery.model.StuckAssessment;
import com.stablebridge.txrecovery.domain.recovery.model.StuckReason;
import com.stablebridge.txrecovery.domain.recovery.model.StuckSeverity;
import com.stablebridge.txrecovery.domain.recovery.port.FeeOracle;
import com.stablebridge.txrecovery.domain.transaction.model.SignedTransaction;
import com.stablebridge.txrecovery.domain.transaction.model.UnsignedTransaction;
import com.stablebridge.txrecovery.domain.transaction.port.TransactionSigner;

@ExtendWith(MockitoExtension.class)
class EvmRecoveryStrategyTest {

    @Mock
    private EvmRpcClient rpcClient;

    @Mock
    private FeeOracle feeOracle;

    @Mock
    private TransactionSigner signer;

    private EvmRecoveryStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new EvmRecoveryStrategy(rpcClient, feeOracle, SOME_CHAIN_ID);
    }

    @Nested
    class AppliesTo {

        @Test
        void shouldReturnTrueForEvm() {
            // when
            var result = strategy.appliesTo(ChainFamily.EVM);

            // then
            assertThat(result).isTrue();
        }

        @Test
        void shouldReturnFalseForSolana() {
            // when
            var result = strategy.appliesTo(ChainFamily.SOLANA);

            // then
            assertThat(result).isFalse();
        }
    }

    @Nested
    class Assess {

        @Nested
        class WhenTransactionHasReceipt {

            @Test
            void shouldReturnWaitWithNotSeenReason() {
                // given
                var transaction = someStuckTransaction();
                given(rpcClient.getTransactionReceipt(SOME_TX_HASH))
                        .willReturn(Optional.of(SOME_RECEIPT));

                // when
                var result = strategy.assess(transaction);

                // then
                var expected = StuckAssessment.builder()
                        .reason(StuckReason.NOT_SEEN)
                        .severity(StuckSeverity.LOW)
                        .recommendedPlan(RecoveryPlan.Wait.builder()
                                .estimatedClearance(Duration.ofMinutes(5))
                                .reason("Transaction has receipt, may be confirming")
                                .build())
                        .explanation("Transaction already has receipt on chain")
                        .build();
                assertThat(result).usingRecursiveComparison().isEqualTo(expected);
            }
        }

        @Nested
        class WhenTransactionDroppedFromMempool {

            @Test
            void shouldReturnMempoolDroppedWithSpeedUpPlan() {
                // given
                var transaction = someStuckTransaction();
                given(rpcClient.getTransactionReceipt(SOME_TX_HASH))
                        .willReturn(Optional.empty());
                given(rpcClient.getTransactionByHash(SOME_TX_HASH))
                        .willReturn(Optional.empty());
                given(feeOracle.estimate(SOME_CHAIN, FeeUrgency.URGENT))
                        .willReturn(SOME_URGENT_FEE);

                // when
                var result = strategy.assess(transaction);

                // then
                var expected = StuckAssessment.builder()
                        .reason(StuckReason.MEMPOOL_DROPPED)
                        .severity(StuckSeverity.HIGH)
                        .recommendedPlan(RecoveryPlan.SpeedUp.builder()
                                .originalTxHash(SOME_TX_HASH)
                                .newFee(SOME_URGENT_FEE)
                                .build())
                        .explanation("Transaction not found in mempool and no receipt — likely dropped")
                        .build();
                assertThat(result).usingRecursiveComparison().isEqualTo(expected);
            }
        }

        @Nested
        class WhenNonceGapDetected {

            @Test
            void shouldReturnNonceGapWithHighSeverity() {
                // given
                var transaction = someStuckTransaction();
                given(rpcClient.getTransactionReceipt(SOME_TX_HASH))
                        .willReturn(Optional.empty());
                given(rpcClient.getTransactionByHash(SOME_TX_HASH))
                        .willReturn(Optional.of(SOME_MEMPOOL_TRANSACTION));
                given(rpcClient.getTransactionCount(SOME_FROM_ADDRESS, "latest"))
                        .willReturn(BigInteger.valueOf(3));
                given(feeOracle.estimateReplacement(SOME_CHAIN, SOME_TX_HASH, 1))
                        .willReturn(SOME_REPLACEMENT_FEE);

                // when
                var result = strategy.assess(transaction);

                // then
                var expected = StuckAssessment.builder()
                        .reason(StuckReason.NONCE_GAP)
                        .severity(StuckSeverity.HIGH)
                        .recommendedPlan(RecoveryPlan.SpeedUp.builder()
                                .originalTxHash(SOME_TX_HASH)
                                .newFee(SOME_REPLACEMENT_FEE)
                                .build())
                        .explanation("Nonce gap detected: on-chain nonce=3, transaction nonce=5")
                        .build();
                assertThat(result).usingRecursiveComparison().isEqualTo(expected);
            }
        }

        @Nested
        class WhenTransactionUnderpriced {

            @Test
            void shouldReturnUnderpricedWithMediumSeverity() {
                // given
                var transaction = someStuckTransaction();
                given(rpcClient.getTransactionReceipt(SOME_TX_HASH))
                        .willReturn(Optional.empty());
                given(rpcClient.getTransactionByHash(SOME_TX_HASH))
                        .willReturn(Optional.of(SOME_LOW_FEE_MEMPOOL_TRANSACTION));
                given(rpcClient.getTransactionCount(SOME_FROM_ADDRESS, "latest"))
                        .willReturn(BigInteger.valueOf(5));
                given(feeOracle.estimate(SOME_CHAIN, FeeUrgency.FAST))
                        .willReturn(SOME_FAST_FEE);
                given(feeOracle.estimateReplacement(SOME_CHAIN, SOME_TX_HASH, 1))
                        .willReturn(SOME_REPLACEMENT_FEE);

                // when
                var result = strategy.assess(transaction);

                // then
                var expected = StuckAssessment.builder()
                        .reason(StuckReason.UNDERPRICED)
                        .severity(StuckSeverity.MEDIUM)
                        .recommendedPlan(RecoveryPlan.SpeedUp.builder()
                                .originalTxHash(SOME_TX_HASH)
                                .newFee(SOME_REPLACEMENT_FEE)
                                .build())
                        .explanation(
                                "Transaction underpriced: current fast=30000000000 wei, submission=5000000000 wei, ratio=6.00")
                        .build();
                assertThat(result).usingRecursiveComparison().isEqualTo(expected);
            }
        }

        @Nested
        class WhenTransactionHasAdequateGas {

            @Test
            void shouldReturnNotPropagatedWithWaitPlan() {
                // given
                var transaction = someStuckTransaction();
                given(rpcClient.getTransactionReceipt(SOME_TX_HASH))
                        .willReturn(Optional.empty());
                given(rpcClient.getTransactionByHash(SOME_TX_HASH))
                        .willReturn(Optional.of(SOME_MEMPOOL_TRANSACTION));
                given(rpcClient.getTransactionCount(SOME_FROM_ADDRESS, "latest"))
                        .willReturn(BigInteger.valueOf(5));
                given(feeOracle.estimate(SOME_CHAIN, FeeUrgency.FAST))
                        .willReturn(SOME_LOW_FAST_FEE);

                // when
                var result = strategy.assess(transaction);

                // then
                var expected = StuckAssessment.builder()
                        .reason(StuckReason.NOT_PROPAGATED)
                        .severity(StuckSeverity.LOW)
                        .recommendedPlan(RecoveryPlan.Wait.builder()
                                .estimatedClearance(Duration.ofMinutes(5))
                                .reason("Transaction in mempool with adequate fee, waiting for inclusion")
                                .build())
                        .explanation("Transaction in mempool with adequate gas price, pending network inclusion")
                        .build();
                assertThat(result).usingRecursiveComparison().isEqualTo(expected);
            }
        }
    }

    @Nested
    class Execute {

        @Nested
        class SpeedUp {

            @Test
            void shouldBuildReplacementAndBroadcast() {
                // given
                var plan = RecoveryPlan.SpeedUp.builder()
                        .originalTxHash(SOME_TX_HASH)
                        .newFee(SOME_REPLACEMENT_FEE)
                        .build();
                var signedTx = SignedTransaction.builder()
                        .intentId("recovery-speedup-" + SOME_TX_HASH)
                        .chain(SOME_CHAIN)
                        .signedPayload(new byte[] {0x01, 0x02, 0x03})
                        .signerAddress(SOME_FROM_ADDRESS)
                        .build();
                var expectedUnsigned = UnsignedTransaction.builder()
                        .intentId("recovery-speedup-" + SOME_TX_HASH)
                        .chain(SOME_CHAIN)
                        .fromAddress(SOME_FROM_ADDRESS)
                        .toAddress(SOME_MEMPOOL_TRANSACTION.to())
                        .payload(new byte[] {0})
                        .feeEstimate(SOME_REPLACEMENT_FEE)
                        .metadata(Map.of(
                                "nonce", "5",
                                "gasLimit", "65000",
                                "type", "0x02",
                                "chainId", "1",
                                "recoveryAction", "SPEED_UP",
                                "originalTxHash", SOME_TX_HASH))
                        .build();

                given(rpcClient.getTransactionByHash(SOME_TX_HASH))
                        .willReturn(Optional.of(SOME_MEMPOOL_TRANSACTION));
                given(rpcClient.getChain()).willReturn(SOME_CHAIN);
                given(signer.sign(eqIgnoring(expectedUnsigned, "payload"), eqIgnoringTimestamps(SOME_FROM_ADDRESS)))
                        .willReturn(signedTx);
                given(rpcClient.sendRawTransaction("0x010203"))
                        .willReturn(SOME_REPLACEMENT_TX_HASH);

                // when
                var result = strategy.execute(plan, signer);

                // then
                var expected = RecoveryResult.builder()
                        .outcome(RecoveryOutcome.REPLACEMENT_SUBMITTED)
                        .replacementTxHash(SOME_REPLACEMENT_TX_HASH)
                        .gasCost(SOME_REPLACEMENT_FEE.estimatedCost())
                        .details("Speed-up replacement submitted with nonce=5, maxFeePerGas=55000000000 wei")
                        .build();
                assertThat(result).usingRecursiveComparison().isEqualTo(expected);
            }

            @Test
            void shouldThrowWhenOriginalTransactionNotFound() {
                // given
                var plan = RecoveryPlan.SpeedUp.builder()
                        .originalTxHash(SOME_TX_HASH)
                        .newFee(SOME_REPLACEMENT_FEE)
                        .build();
                given(rpcClient.getTransactionByHash(SOME_TX_HASH))
                        .willReturn(Optional.empty());

                // when/then
                assertThatThrownBy(() -> strategy.execute(plan, signer))
                        .isInstanceOf(EvmRpcException.class)
                        .hasMessageContaining("Original transaction not found for speed-up");
            }
        }

        @Nested
        class Cancel {

            @Test
            void shouldBuildSelfTransferAndBroadcast() {
                // given
                var plan = RecoveryPlan.Cancel.builder()
                        .originalTxHash(SOME_TX_HASH)
                        .build();
                var signedTx = SignedTransaction.builder()
                        .intentId("recovery-cancel-" + SOME_TX_HASH)
                        .chain(SOME_CHAIN)
                        .signedPayload(new byte[] {0x04, 0x05, 0x06})
                        .signerAddress(SOME_FROM_ADDRESS)
                        .build();
                var expectedUnsigned = UnsignedTransaction.builder()
                        .intentId("recovery-cancel-" + SOME_TX_HASH)
                        .chain(SOME_CHAIN)
                        .fromAddress(SOME_FROM_ADDRESS)
                        .toAddress(SOME_FROM_ADDRESS)
                        .payload(new byte[] {0})
                        .feeEstimate(SOME_FAST_FEE)
                        .metadata(Map.of(
                                "nonce", "5",
                                "gasLimit", "21000",
                                "type", "0x02",
                                "chainId", "1",
                                "recoveryAction", "CANCEL",
                                "originalTxHash", SOME_TX_HASH))
                        .build();

                given(rpcClient.getTransactionByHash(SOME_TX_HASH))
                        .willReturn(Optional.of(SOME_MEMPOOL_TRANSACTION));
                given(rpcClient.getChain()).willReturn(SOME_CHAIN);
                given(feeOracle.estimate(SOME_CHAIN, FeeUrgency.FAST))
                        .willReturn(SOME_FAST_FEE);
                given(signer.sign(eqIgnoring(expectedUnsigned, "payload"), eqIgnoringTimestamps(SOME_FROM_ADDRESS)))
                        .willReturn(signedTx);
                given(rpcClient.sendRawTransaction("0x040506"))
                        .willReturn(SOME_REPLACEMENT_TX_HASH);

                // when
                var result = strategy.execute(plan, signer);

                // then
                var expected = RecoveryResult.builder()
                        .outcome(RecoveryOutcome.REPLACEMENT_SUBMITTED)
                        .replacementTxHash(SOME_REPLACEMENT_TX_HASH)
                        .gasCost(SOME_FAST_FEE.estimatedCost())
                        .details("Cancel self-transfer submitted with nonce=5, gasLimit=21000")
                        .build();
                assertThat(result).usingRecursiveComparison().isEqualTo(expected);
            }

            @Test
            void shouldThrowWhenOriginalTransactionNotFound() {
                // given
                var plan = RecoveryPlan.Cancel.builder()
                        .originalTxHash(SOME_TX_HASH)
                        .build();
                given(rpcClient.getTransactionByHash(SOME_TX_HASH))
                        .willReturn(Optional.empty());

                // when/then
                assertThatThrownBy(() -> strategy.execute(plan, signer))
                        .isInstanceOf(EvmRpcException.class)
                        .hasMessageContaining("Original transaction not found for cancel");
            }
        }

        @Nested
        class Resubmit {

            @Test
            void shouldThrowIllegalStateException() {
                // given
                var plan = RecoveryPlan.Resubmit.builder()
                        .originalTxHash(SOME_TX_HASH)
                        .build();

                // when/then
                assertThatThrownBy(() -> strategy.execute(plan, signer))
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("EVM does not support Resubmit");
            }
        }

        @Nested
        class Wait {

            @Test
            void shouldReturnWaitingResult() {
                // given
                var plan = RecoveryPlan.Wait.builder()
                        .estimatedClearance(Duration.ofMinutes(10))
                        .reason("Network congestion clearing")
                        .build();

                // when
                var result = strategy.execute(plan, signer);

                // then
                var expected = RecoveryResult.builder()
                        .outcome(RecoveryOutcome.WAITING)
                        .details("Waiting estimated PT10M: Network congestion clearing")
                        .build();
                assertThat(result).usingRecursiveComparison().isEqualTo(expected);
                then(signer).shouldHaveNoInteractions();
            }
        }
    }
}
