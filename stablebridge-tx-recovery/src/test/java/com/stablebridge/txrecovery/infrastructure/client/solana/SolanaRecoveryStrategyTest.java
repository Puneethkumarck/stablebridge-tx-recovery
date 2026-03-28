package com.stablebridge.txrecovery.infrastructure.client.solana;

import static com.stablebridge.txrecovery.testutil.TestUtils.eqIgnoring;
import static com.stablebridge.txrecovery.testutil.fixtures.SolanaRecoveryFixtures.SOME_CHAIN;
import static com.stablebridge.txrecovery.testutil.fixtures.SolanaRecoveryFixtures.SOME_FROM_ADDRESS;
import static com.stablebridge.txrecovery.testutil.fixtures.SolanaRecoveryFixtures.SOME_NEW_NONCE_VALUE;
import static com.stablebridge.txrecovery.testutil.fixtures.SolanaRecoveryFixtures.SOME_NONCE_ACCOUNT;
import static com.stablebridge.txrecovery.testutil.fixtures.SolanaRecoveryFixtures.SOME_NONCE_VALUE;
import static com.stablebridge.txrecovery.testutil.fixtures.SolanaRecoveryFixtures.SOME_REPLACEMENT_TX_HASH;
import static com.stablebridge.txrecovery.testutil.fixtures.SolanaRecoveryFixtures.SOME_TX_HASH;
import static com.stablebridge.txrecovery.testutil.fixtures.SolanaRecoveryFixtures.SOME_URGENT_FEE;
import static com.stablebridge.txrecovery.testutil.fixtures.SolanaRecoveryFixtures.someStuckSolanaTransaction;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.stablebridge.txrecovery.domain.address.model.ChainFamily;
import com.stablebridge.txrecovery.domain.recovery.model.RecoveryOutcome;
import com.stablebridge.txrecovery.domain.recovery.model.RecoveryPlan;
import com.stablebridge.txrecovery.domain.recovery.model.RecoveryResult;
import com.stablebridge.txrecovery.domain.recovery.model.StuckAssessment;
import com.stablebridge.txrecovery.domain.recovery.model.StuckReason;
import com.stablebridge.txrecovery.domain.recovery.model.StuckSeverity;
import com.stablebridge.txrecovery.domain.transaction.model.SignedTransaction;
import com.stablebridge.txrecovery.domain.transaction.model.SolanaSubmissionResource;
import com.stablebridge.txrecovery.domain.transaction.model.UnsignedTransaction;
import com.stablebridge.txrecovery.domain.transaction.port.SubmissionResourceManager;
import com.stablebridge.txrecovery.domain.transaction.port.TransactionSigner;

@ExtendWith(MockitoExtension.class)
class SolanaRecoveryStrategyTest {

    @Mock
    private SolanaRpcClient rpcClient;

    @Mock
    private SolanaTransactionBuilder transactionBuilder;

    @Mock
    private SubmissionResourceManager submissionResourceManager;

    @Mock
    private TransactionSigner signer;

    private SolanaRecoveryStrategy strategy;

    private static final SolanaSignatureStatus SOME_CONFIRMED_STATUS = SolanaSignatureStatus.builder()
            .slot(100L)
            .confirmations(32L)
            .confirmationStatus("confirmed")
            .build();

    private static final SolanaSignatureStatus SOME_ERROR_STATUS = SolanaSignatureStatus.builder()
            .slot(100L)
            .confirmations(null)
            .confirmationStatus("processed")
            .err("TransactionError")
            .build();

    @BeforeEach
    void setUp() {
        strategy = new SolanaRecoveryStrategy(rpcClient, transactionBuilder, submissionResourceManager);
    }

    @Nested
    class AppliesTo {

        @Test
        void shouldReturnTrueForSolana() {
            // when
            var result = strategy.appliesTo(ChainFamily.SOLANA);

            // then
            assertThat(result).isTrue();
        }

        @Test
        void shouldReturnFalseForEvm() {
            // when
            var result = strategy.appliesTo(ChainFamily.EVM);

            // then
            assertThat(result).isFalse();
        }
    }

    @Nested
    class Assess {

        @Nested
        class WhenTransactionConfirming {

            @Test
            void shouldReturnWaitWithNotPropagatedReason() {
                // given
                var transaction = someStuckSolanaTransaction();
                given(rpcClient.getSignatureStatuses(List.of(SOME_TX_HASH)))
                        .willReturn(List.of(SOME_CONFIRMED_STATUS));

                // when
                var result = strategy.assess(transaction);

                // then
                var expected = StuckAssessment.builder()
                        .reason(StuckReason.NOT_PROPAGATED)
                        .severity(StuckSeverity.LOW)
                        .recommendedPlan(RecoveryPlan.Wait.builder()
                                .estimatedClearance(Duration.ofMinutes(5))
                                .reason("Transaction is confirming on chain")
                                .build())
                        .explanation("Transaction already confirmed or finalizing on chain")
                        .build();
                assertThat(result).usingRecursiveComparison().isEqualTo(expected);
            }
        }

        @Nested
        class WhenTransactionHasError {

            @Test
            void shouldReturnExpiredWithResubmitPlan() {
                // given
                var transaction = someStuckSolanaTransaction();
                given(rpcClient.getSignatureStatuses(List.of(SOME_TX_HASH)))
                        .willReturn(List.of(SOME_ERROR_STATUS));

                // when
                var result = strategy.assess(transaction);

                // then
                var expected = StuckAssessment.builder()
                        .reason(StuckReason.EXPIRED)
                        .severity(StuckSeverity.HIGH)
                        .recommendedPlan(RecoveryPlan.Resubmit.builder()
                                .originalTxHash(SOME_TX_HASH)
                                .build())
                        .explanation("Transaction failed on chain with error")
                        .build();
                assertThat(result).usingRecursiveComparison().isEqualTo(expected);
            }
        }

        @Nested
        class WhenNonceConsumed {

            @Test
            void shouldReturnNonceConsumedWithWaitPlan() {
                // given
                var transaction = someStuckSolanaTransaction();
                given(rpcClient.getSignatureStatuses(List.of(SOME_TX_HASH)))
                        .willReturn(List.of());
                given(rpcClient.getNonce(SOME_NONCE_ACCOUNT, SolanaCommitment.CONFIRMED))
                        .willReturn(SOME_NEW_NONCE_VALUE);

                // when
                var result = strategy.assess(transaction);

                // then
                var expected = StuckAssessment.builder()
                        .reason(StuckReason.NONCE_CONSUMED)
                        .severity(StuckSeverity.HIGH)
                        .recommendedPlan(RecoveryPlan.Wait.builder()
                                .estimatedClearance(Duration.ofMinutes(5))
                                .reason("Nonce already advanced, transaction may have been processed")
                                .build())
                        .explanation("Durable nonce consumed: current=%s, original=%s"
                                .formatted(SOME_NEW_NONCE_VALUE, SOME_NONCE_VALUE))
                        .build();
                assertThat(result).usingRecursiveComparison().isEqualTo(expected);
            }
        }

        @Nested
        class WhenBlockhashExpired {

            @Test
            void shouldReturnExpiredWithResubmitPlan() {
                // given
                var transaction = someStuckSolanaTransaction();
                given(rpcClient.getSignatureStatuses(List.of(SOME_TX_HASH)))
                        .willReturn(List.of());
                given(rpcClient.getNonce(SOME_NONCE_ACCOUNT, SolanaCommitment.CONFIRMED))
                        .willReturn(SOME_NONCE_VALUE);
                given(rpcClient.isBlockhashValid(SOME_NONCE_VALUE, SolanaCommitment.CONFIRMED))
                        .willReturn(false);

                // when
                var result = strategy.assess(transaction);

                // then
                var expected = StuckAssessment.builder()
                        .reason(StuckReason.EXPIRED)
                        .severity(StuckSeverity.HIGH)
                        .recommendedPlan(RecoveryPlan.Resubmit.builder()
                                .originalTxHash(SOME_TX_HASH)
                                .build())
                        .explanation("Blockhash expired for nonce value: " + SOME_NONCE_VALUE)
                        .build();
                assertThat(result).usingRecursiveComparison().isEqualTo(expected);
            }
        }

        @Nested
        class WhenTransactionNotPropagated {

            @Test
            void shouldReturnNotPropagatedWithWaitPlan() {
                // given
                var transaction = someStuckSolanaTransaction();
                given(rpcClient.getSignatureStatuses(List.of(SOME_TX_HASH)))
                        .willReturn(List.of());
                given(rpcClient.getNonce(SOME_NONCE_ACCOUNT, SolanaCommitment.CONFIRMED))
                        .willReturn(SOME_NONCE_VALUE);
                given(rpcClient.isBlockhashValid(SOME_NONCE_VALUE, SolanaCommitment.CONFIRMED))
                        .willReturn(true);

                // when
                var result = strategy.assess(transaction);

                // then
                var expected = StuckAssessment.builder()
                        .reason(StuckReason.NOT_PROPAGATED)
                        .severity(StuckSeverity.LOW)
                        .recommendedPlan(RecoveryPlan.Wait.builder()
                                .estimatedClearance(Duration.ofMinutes(5))
                                .reason("Transaction not yet propagated, blockhash still valid")
                                .build())
                        .explanation("Transaction not found but blockhash still valid — waiting for propagation")
                        .build();
                assertThat(result).usingRecursiveComparison().isEqualTo(expected);
            }
        }
    }

    @Nested
    class Execute {

        @Nested
        class Resubmit {

            @Test
            void shouldAcquireFreshResourceAndBroadcast() {
                // given
                var transaction = someStuckSolanaTransaction();
                given(rpcClient.getSignatureStatuses(List.of(SOME_TX_HASH)))
                        .willReturn(List.of(SOME_ERROR_STATUS));
                strategy.assess(transaction);

                var plan = RecoveryPlan.Resubmit.builder()
                        .originalTxHash(SOME_TX_HASH)
                        .build();

                var freshResource = SolanaSubmissionResource.builder()
                        .chain(SOME_CHAIN)
                        .fromAddress(SOME_FROM_ADDRESS)
                        .nonceAccountAddress("FreshNonceAccount1111111111111111111111111")
                        .nonceValue("FreshNonceValue11111111111111111111111111111")
                        .build();

                var expectedIntent =
                        com.stablebridge.txrecovery.domain.transaction.model.TransactionIntent.builder()
                                .intentId("recovery-resubmit-" + SOME_TX_HASH)
                                .chain(SOME_CHAIN)
                                .toAddress(SOME_FROM_ADDRESS)
                                .amount(java.math.BigDecimal.ZERO)
                                .token("SOL")
                                .build();

                var unsignedTx = UnsignedTransaction.builder()
                        .intentId("recovery-resubmit-" + SOME_TX_HASH)
                        .chain(SOME_CHAIN)
                        .fromAddress(SOME_FROM_ADDRESS)
                        .toAddress(SOME_FROM_ADDRESS)
                        .payload(new byte[] {0x01, 0x02})
                        .feeEstimate(SOME_URGENT_FEE)
                        .build();

                var signedTx = SignedTransaction.builder()
                        .intentId("recovery-resubmit-" + SOME_TX_HASH)
                        .chain(SOME_CHAIN)
                        .signedPayload(new byte[] {0x03, 0x04})
                        .signerAddress(SOME_FROM_ADDRESS)
                        .build();

                given(submissionResourceManager.acquire(eqIgnoring(expectedIntent, "metadata")))
                        .willReturn(freshResource);
                given(transactionBuilder.build(
                                eqIgnoring(expectedIntent, "metadata"), eqIgnoring(freshResource)))
                        .willReturn(unsignedTx);
                given(signer.sign(eqIgnoring(unsignedTx, "payload"), eqIgnoring(SOME_FROM_ADDRESS)))
                        .willReturn(signedTx);
                given(rpcClient.sendTransaction(signedTx.signedPayload()))
                        .willReturn(SOME_REPLACEMENT_TX_HASH);

                // when
                var result = strategy.execute(plan, signer);

                // then
                var expected = RecoveryResult.builder()
                        .outcome(RecoveryOutcome.REPLACEMENT_SUBMITTED)
                        .replacementTxHash(SOME_REPLACEMENT_TX_HASH)
                        .details("Resubmit with fresh nonce: nonceAccount=%s"
                                .formatted(freshResource.nonceAccountAddress()))
                        .build();
                assertThat(result).usingRecursiveComparison().isEqualTo(expected);
            }

            @Test
            void shouldReleaseOldResourceOnSuccess() {
                // given
                var transaction = someStuckSolanaTransaction();
                given(rpcClient.getSignatureStatuses(List.of(SOME_TX_HASH)))
                        .willReturn(List.of(SOME_ERROR_STATUS));
                strategy.assess(transaction);

                var plan = RecoveryPlan.Resubmit.builder()
                        .originalTxHash(SOME_TX_HASH)
                        .build();

                var originalResource = (SolanaSubmissionResource) transaction.resource();

                var freshResource = SolanaSubmissionResource.builder()
                        .chain(SOME_CHAIN)
                        .fromAddress(SOME_FROM_ADDRESS)
                        .nonceAccountAddress("FreshNonceAccount1111111111111111111111111")
                        .nonceValue("FreshNonceValue11111111111111111111111111111")
                        .build();

                var expectedIntent =
                        com.stablebridge.txrecovery.domain.transaction.model.TransactionIntent.builder()
                                .intentId("recovery-resubmit-" + SOME_TX_HASH)
                                .chain(SOME_CHAIN)
                                .toAddress(SOME_FROM_ADDRESS)
                                .amount(java.math.BigDecimal.ZERO)
                                .token("SOL")
                                .build();

                var unsignedTx = UnsignedTransaction.builder()
                        .intentId("recovery-resubmit-" + SOME_TX_HASH)
                        .chain(SOME_CHAIN)
                        .fromAddress(SOME_FROM_ADDRESS)
                        .toAddress(SOME_FROM_ADDRESS)
                        .payload(new byte[] {0x01, 0x02})
                        .feeEstimate(SOME_URGENT_FEE)
                        .build();

                var signedTx = SignedTransaction.builder()
                        .intentId("recovery-resubmit-" + SOME_TX_HASH)
                        .chain(SOME_CHAIN)
                        .signedPayload(new byte[] {0x03, 0x04})
                        .signerAddress(SOME_FROM_ADDRESS)
                        .build();

                given(submissionResourceManager.acquire(eqIgnoring(expectedIntent, "metadata")))
                        .willReturn(freshResource);
                given(transactionBuilder.build(
                                eqIgnoring(expectedIntent, "metadata"), eqIgnoring(freshResource)))
                        .willReturn(unsignedTx);
                given(signer.sign(eqIgnoring(unsignedTx, "payload"), eqIgnoring(SOME_FROM_ADDRESS)))
                        .willReturn(signedTx);
                given(rpcClient.sendTransaction(signedTx.signedPayload()))
                        .willReturn(SOME_REPLACEMENT_TX_HASH);

                // when
                strategy.execute(plan, signer);

                // then
                then(submissionResourceManager).should().release(originalResource);
            }

            @Test
            void shouldReleaseFreshResourceOnBuildFailure() {
                // given
                var transaction = someStuckSolanaTransaction();
                given(rpcClient.getSignatureStatuses(List.of(SOME_TX_HASH)))
                        .willReturn(List.of(SOME_ERROR_STATUS));
                strategy.assess(transaction);

                var plan = RecoveryPlan.Resubmit.builder()
                        .originalTxHash(SOME_TX_HASH)
                        .build();

                var freshResource = SolanaSubmissionResource.builder()
                        .chain(SOME_CHAIN)
                        .fromAddress(SOME_FROM_ADDRESS)
                        .nonceAccountAddress("FreshNonceAccount1111111111111111111111111")
                        .nonceValue("FreshNonceValue11111111111111111111111111111")
                        .build();

                var expectedIntent =
                        com.stablebridge.txrecovery.domain.transaction.model.TransactionIntent.builder()
                                .intentId("recovery-resubmit-" + SOME_TX_HASH)
                                .chain(SOME_CHAIN)
                                .toAddress(SOME_FROM_ADDRESS)
                                .amount(java.math.BigDecimal.ZERO)
                                .token("SOL")
                                .build();

                given(submissionResourceManager.acquire(eqIgnoring(expectedIntent, "metadata")))
                        .willReturn(freshResource);
                given(transactionBuilder.build(
                                eqIgnoring(expectedIntent, "metadata"), eqIgnoring(freshResource)))
                        .willThrow(new RuntimeException("Build failed"));

                // when/then
                assertThatThrownBy(() -> strategy.execute(plan, signer))
                        .isInstanceOf(RuntimeException.class)
                        .hasMessageContaining("Build failed");
                then(submissionResourceManager).should().release(freshResource);
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

        @Nested
        class SpeedUp {

            @Test
            void shouldThrowIllegalStateException() {
                // given
                var plan = RecoveryPlan.SpeedUp.builder()
                        .originalTxHash(SOME_TX_HASH)
                        .newFee(SOME_URGENT_FEE)
                        .build();

                // when/then
                assertThatThrownBy(() -> strategy.execute(plan, signer))
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("Solana does not support SpeedUp");
            }
        }

        @Nested
        class Cancel {

            @Test
            void shouldThrowIllegalStateException() {
                // given
                var plan = RecoveryPlan.Cancel.builder()
                        .originalTxHash(SOME_TX_HASH)
                        .build();

                // when/then
                assertThatThrownBy(() -> strategy.execute(plan, signer))
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("Solana does not support Cancel");
            }
        }
    }
}
