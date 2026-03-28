package com.stablebridge.txrecovery.application.workflow;

import static com.stablebridge.txrecovery.domain.transaction.model.TransactionStatus.*;
import static com.stablebridge.txrecovery.testutil.TestUtils.eqIgnoring;
import static com.stablebridge.txrecovery.testutil.fixtures.TransactionIntentFixtures.*;
import static com.stablebridge.txrecovery.testutil.fixtures.WorkflowTestFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.stablebridge.txrecovery.domain.address.model.AddressTier;
import com.stablebridge.txrecovery.domain.exception.NonRetryableException;
import com.stablebridge.txrecovery.domain.recovery.model.ApprovalAction;
import com.stablebridge.txrecovery.domain.recovery.model.CancelRequest;
import com.stablebridge.txrecovery.domain.recovery.model.HumanApproval;
import com.stablebridge.txrecovery.domain.recovery.model.RecoveryOutcome;
import com.stablebridge.txrecovery.domain.recovery.model.RecoveryPlan;
import com.stablebridge.txrecovery.domain.recovery.model.RecoveryResult;
import com.stablebridge.txrecovery.domain.recovery.model.StuckAssessment;
import com.stablebridge.txrecovery.domain.recovery.model.StuckReason;
import com.stablebridge.txrecovery.domain.recovery.model.StuckSeverity;
import com.stablebridge.txrecovery.domain.transaction.event.TransactionLifecycleEvent;
import com.stablebridge.txrecovery.domain.transaction.model.BroadcastResult;
import com.stablebridge.txrecovery.domain.transaction.model.ConfirmationStatus;
import com.stablebridge.txrecovery.domain.transaction.model.EvmSubmissionResource;
import com.stablebridge.txrecovery.domain.transaction.model.SubmittedTransaction;
import com.stablebridge.txrecovery.domain.transaction.model.TransactionIntent;
import com.stablebridge.txrecovery.domain.transaction.model.TransactionResult;
import com.stablebridge.txrecovery.domain.transaction.model.TransactionSnapshot;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;

class TransactionLifecycleWorkflowImplTest {

    private static final String TASK_QUEUE = "str-transaction-lifecycle";

    private TestWorkflowEnvironment testEnv;
    private Worker worker;
    private WorkflowClient client;
    private TransactionLifecycleActivities activities;

    @BeforeEach
    void setUp() {
        testEnv = TestWorkflowEnvironment.newInstance();
        worker = testEnv.newWorker(TASK_QUEUE);
        worker.registerWorkflowImplementationTypes(TransactionLifecycleWorkflowImpl.class);
        activities = mock(TransactionLifecycleActivities.class);
        worker.registerActivitiesImplementations(activities);
        client = testEnv.getWorkflowClient();
    }

    @AfterEach
    void tearDown() {
        testEnv.close();
    }

    @Nested
    class HappyPath {

        @Test
        void shouldProcessTransactionToFinalized() {
            // given
            var resource = someEvmResource();
            var unsigned = someUnsignedTransaction();
            var signed = someSignedTransaction();
            var broadcastResult = someBroadcastResult();
            var confirmation = someFinalizedConfirmation();

            given(activities.acquireResource(eqIgnoring(SOME_SEQUENTIAL_INTENT, "amount", "rawAmount"))).willReturn(resource);
            given(activities.build(eqIgnoring(SOME_SEQUENTIAL_INTENT, "amount", "rawAmount"), eqIgnoring(resource))).willReturn(unsigned);
            given(activities.sign(eqIgnoring(unsigned, "payload"), eqIgnoring(SOME_FROM_ADDRESS))).willReturn(signed);
            given(activities.broadcast(eqIgnoring(signed, "signedPayload"), eqIgnoring(SOME_CHAIN))).willReturn(broadcastResult);
            given(activities.checkStatus(SOME_TX_HASH, SOME_CHAIN)).willReturn(CONFIRMED);
            given(activities.waitForFinality(SOME_TX_HASH, SOME_CHAIN)).willReturn(confirmation);

            // when
            testEnv.start();
            var workflow = startWorkflow(SOME_SEQUENTIAL_INTENT);
            var result = workflow.process(SOME_SEQUENTIAL_INTENT, null);

            // then
            var expected = TransactionResult.builder()
                    .transactionId(result.transactionId())
                    .intentId(SOME_INTENT_ID)
                    .finalStatus(FINALIZED)
                    .txHash(SOME_TX_HASH)
                    .totalGasSpent(BigDecimal.ZERO)
                    .gasDenomination("ETH")
                    .totalAttempts(0)
                    .completedAt(result.completedAt())
                    .build();
            assertThat(result)
                    .usingRecursiveComparison()
                    .isEqualTo(expected);

            then(activities).should().consumeResource(eqIgnoring(resource));
        }
    }

    @Nested
    class CancelSignal {

        @Test
        void shouldCancelTransactionOnSignal() {
            // given
            var resource = someEvmResource();
            var unsigned = someUnsignedTransaction();
            var signed = someSignedTransaction();
            var broadcastResult = someBroadcastResult();

            given(activities.acquireResource(eqIgnoring(SOME_SEQUENTIAL_INTENT, "amount", "rawAmount"))).willReturn(resource);
            given(activities.build(eqIgnoring(SOME_SEQUENTIAL_INTENT, "amount", "rawAmount"), eqIgnoring(resource))).willReturn(unsigned);
            given(activities.sign(eqIgnoring(unsigned, "payload"), eqIgnoring(SOME_FROM_ADDRESS))).willReturn(signed);
            given(activities.broadcast(eqIgnoring(signed, "signedPayload"), eqIgnoring(SOME_CHAIN))).willReturn(broadcastResult);
            given(activities.checkStatus(SOME_TX_HASH, SOME_CHAIN)).willReturn(PENDING);

            testEnv.start();
            var workflow = startWorkflow(SOME_SEQUENTIAL_INTENT);
            var stub = WorkflowStub.fromTyped(workflow);

            // when
            WorkflowClient.start(workflow::process, SOME_SEQUENTIAL_INTENT, null);

            testEnv.registerDelayedCallback(
                    Duration.ofSeconds(2),
                    () -> workflow.cancelTransaction(CancelRequest.builder()
                            .requestedBy("operator-1")
                            .reason("test cancel")
                            .build()));

            var result = stub.getResult(TransactionResult.class);

            // then
            var expected = TransactionResult.builder()
                    .transactionId(result.transactionId())
                    .intentId(SOME_INTENT_ID)
                    .finalStatus(CANCELLED)
                    .txHash(SOME_TX_HASH)
                    .totalGasSpent(BigDecimal.ZERO)
                    .gasDenomination("ETH")
                    .totalAttempts(0)
                    .completedAt(result.completedAt())
                    .build();
            assertThat(result)
                    .usingRecursiveComparison()
                    .ignoringFields("transactionId", "completedAt", "totalAttempts")
                    .isEqualTo(expected);
            var expectedCancelPlan = RecoveryPlan.Cancel.builder()
                    .originalTxHash(SOME_TX_HASH).build();
            then(activities).should().executeRecovery(eqIgnoring(expectedCancelPlan), eqIgnoring(SOME_CHAIN));
            then(activities).should(atLeastOnce()).releaseResource(eqIgnoring(resource));
        }
    }

    @Nested
    class StuckRecovery {

        @Test
        void shouldRecoverStuckTransactionWithSpeedUp() {
            // given
            var resource = someEvmResource();
            var unsigned = someUnsignedTransaction();
            var signed = someSignedTransaction();
            var broadcastResult = someBroadcastResult();
            var replacementHash = "0xreplacement789";
            var speedUpPlan = someSpeedUpPlan(SOME_TX_HASH);
            var assessment = StuckAssessment.builder()
                    .reason(StuckReason.UNDERPRICED)
                    .severity(StuckSeverity.MEDIUM)
                    .recommendedPlan(speedUpPlan)
                    .explanation("Gas price too low")
                    .build();
            var recoveryResult = RecoveryResult.builder()
                    .outcome(RecoveryOutcome.REPLACEMENT_SUBMITTED)
                    .replacementTxHash(replacementHash)
                    .gasCost(new BigDecimal("0.002"))
                    .build();
            var confirmation = someFinalizedConfirmation();

            given(activities.acquireResource(eqIgnoring(SOME_SEQUENTIAL_INTENT, "amount", "rawAmount"))).willReturn(resource);
            given(activities.build(eqIgnoring(SOME_SEQUENTIAL_INTENT, "amount", "rawAmount"), eqIgnoring(resource))).willReturn(unsigned);
            given(activities.sign(eqIgnoring(unsigned, "payload"), eqIgnoring(SOME_FROM_ADDRESS))).willReturn(signed);
            given(activities.broadcast(eqIgnoring(signed, "signedPayload"), eqIgnoring(SOME_CHAIN))).willReturn(broadcastResult);
            given(activities.checkStatus(SOME_TX_HASH, SOME_CHAIN)).willReturn(STUCK);
            given(activities.assessStuck(eqIgnoring(buildExpectedSubmitted(), "submittedAt", "gasSpent", "gasBudget", "transactionId", "currentTier", "retryCount"))).willReturn(assessment);
            given(activities.executeRecovery(eqIgnoring(speedUpPlan), eqIgnoring(SOME_CHAIN))).willReturn(recoveryResult);
            given(activities.checkStatus(replacementHash, SOME_CHAIN)).willReturn(CONFIRMED);
            given(activities.waitForFinality(replacementHash, SOME_CHAIN)).willReturn(confirmation);

            // when
            testEnv.start();
            var workflow = startWorkflow(SOME_SEQUENTIAL_INTENT);
            var result = workflow.process(SOME_SEQUENTIAL_INTENT, null);

            // then
            var expected = TransactionResult.builder()
                    .transactionId(result.transactionId())
                    .intentId(SOME_INTENT_ID)
                    .finalStatus(FINALIZED)
                    .txHash(replacementHash)
                    .totalGasSpent(new BigDecimal("0.002"))
                    .gasDenomination("ETH")
                    .totalAttempts(1)
                    .completedAt(result.completedAt())
                    .build();
            assertThat(result)
                    .usingRecursiveComparison()
                    .isEqualTo(expected);
        }
    }

    @Nested
    class DroppedTransaction {

        @Test
        void shouldResubmitDroppedTransaction() {
            // given
            var resource = someEvmResource();
            var newResource = EvmSubmissionResource.builder()
                    .chain(SOME_CHAIN)
                    .fromAddress(SOME_FROM_ADDRESS)
                    .nonce(42L)
                    .tier(AddressTier.HOT)
                    .build();
            var unsigned = someUnsignedTransaction();
            var signed = someSignedTransaction();
            var broadcastResult = someBroadcastResult();
            var newTxHash = "0xnew456";
            var newBroadcast = BroadcastResult.builder()
                    .txHash(newTxHash)
                    .chain(SOME_CHAIN)
                    .broadcastedAt(Instant.parse("2026-01-01T00:01:00Z"))
                    .build();
            var confirmation = ConfirmationStatus.builder()
                    .txHash(newTxHash)
                    .chain(SOME_CHAIN)
                    .confirmations(12)
                    .requiredConfirmations(12)
                    .finalized(true)
                    .build();

            var acquireCount = new AtomicInteger(0);
            given(activities.acquireResource(eqIgnoring(SOME_SEQUENTIAL_INTENT, "amount", "rawAmount")))
                    .willAnswer(_ -> acquireCount.incrementAndGet() == 1 ? resource : newResource);
            given(activities.build(eqIgnoring(SOME_SEQUENTIAL_INTENT, "amount", "rawAmount"), eqIgnoring(resource))).willReturn(unsigned);
            given(activities.build(eqIgnoring(SOME_SEQUENTIAL_INTENT, "amount", "rawAmount"), eqIgnoring(newResource))).willReturn(unsigned);
            given(activities.sign(eqIgnoring(unsigned, "payload"), eqIgnoring(SOME_FROM_ADDRESS))).willReturn(signed);
            var broadcastCount = new AtomicInteger(0);
            given(activities.broadcast(eqIgnoring(signed, "signedPayload"), eqIgnoring(SOME_CHAIN)))
                    .willAnswer(_ -> broadcastCount.incrementAndGet() == 1 ? broadcastResult : newBroadcast);
            given(activities.checkStatus(SOME_TX_HASH, SOME_CHAIN)).willReturn(DROPPED);
            given(activities.checkStatus(newTxHash, SOME_CHAIN)).willReturn(CONFIRMED);
            given(activities.waitForFinality(newTxHash, SOME_CHAIN)).willReturn(confirmation);

            // when
            testEnv.start();
            var workflow = startWorkflow(SOME_SEQUENTIAL_INTENT);
            var result = workflow.process(SOME_SEQUENTIAL_INTENT, null);

            // then
            var expected = TransactionResult.builder()
                    .transactionId(result.transactionId())
                    .intentId(SOME_INTENT_ID)
                    .finalStatus(FINALIZED)
                    .txHash(newTxHash)
                    .totalGasSpent(BigDecimal.ZERO)
                    .gasDenomination("ETH")
                    .totalAttempts(1)
                    .completedAt(result.completedAt())
                    .build();
            assertThat(result)
                    .usingRecursiveComparison()
                    .isEqualTo(expected);
            then(activities).should(atLeastOnce()).releaseResource(eqIgnoring(resource));
            then(activities).should().consumeResource(eqIgnoring(newResource));
        }
    }

    @Nested
    class ApprovalSignal {

        @Test
        void shouldResumeAfterHumanApprovalRetry() {
            // given
            var resource = someEvmResource();
            var unsigned = someUnsignedTransaction();
            var signed = someSignedTransaction();
            var broadcastResult = someBroadcastResult();
            var assessment = StuckAssessment.builder()
                    .reason(StuckReason.UNDERPRICED)
                    .severity(StuckSeverity.HIGH)
                    .recommendedPlan(someSpeedUpPlan(SOME_TX_HASH))
                    .explanation("Gas price too low")
                    .build();
            var confirmation = someFinalizedConfirmation();

            given(activities.acquireResource(eqIgnoring(SOME_SEQUENTIAL_INTENT, "amount", "rawAmount"))).willReturn(resource);
            given(activities.build(eqIgnoring(SOME_SEQUENTIAL_INTENT, "amount", "rawAmount"), eqIgnoring(resource))).willReturn(unsigned);
            given(activities.sign(eqIgnoring(unsigned, "payload"), eqIgnoring(SOME_FROM_ADDRESS))).willReturn(signed);
            given(activities.broadcast(eqIgnoring(signed, "signedPayload"), eqIgnoring(SOME_CHAIN))).willReturn(broadcastResult);
            var checkCount = new AtomicInteger(0);
            given(activities.checkStatus(SOME_TX_HASH, SOME_CHAIN))
                    .willAnswer(_ -> checkCount.incrementAndGet() <= 3 ? STUCK : CONFIRMED);
            given(activities.assessStuck(eqIgnoring(buildExpectedSubmitted(), "submittedAt", "gasSpent", "gasBudget", "transactionId", "currentTier", "retryCount"))).willReturn(assessment);
            given(activities.waitForFinality(SOME_TX_HASH, SOME_CHAIN)).willReturn(confirmation);

            testEnv.start();
            var workflow = startWorkflow(SOME_SEQUENTIAL_INTENT);
            var stub = WorkflowStub.fromTyped(workflow);

            // when
            WorkflowClient.start(workflow::process, SOME_SEQUENTIAL_INTENT, null);

            testEnv.registerDelayedCallback(
                    Duration.ofSeconds(5),
                    () -> workflow.approveRecovery(HumanApproval.builder()
                            .action(ApprovalAction.RETRY)
                            .approvedBy("admin-1")
                            .reason("approved")
                            .approvedAt(Instant.parse("2026-01-01T01:00:00Z"))
                            .build()));

            var result = stub.getResult(TransactionResult.class);

            // then
            var expected = TransactionResult.builder()
                    .transactionId(result.transactionId())
                    .intentId(SOME_INTENT_ID)
                    .finalStatus(FINALIZED)
                    .txHash(SOME_TX_HASH)
                    .totalGasSpent(BigDecimal.ZERO)
                    .gasDenomination("ETH")
                    .totalAttempts(0)
                    .completedAt(result.completedAt())
                    .build();
            assertThat(result)
                    .usingRecursiveComparison()
                    .ignoringFields("transactionId", "completedAt", "totalAttempts", "gasDenomination")
                    .isEqualTo(expected);
        }
    }

    @Nested
    class QueryStatus {

        @Test
        void shouldReturnCurrentSnapshotViaQuery() {
            // given
            var resource = someEvmResource();
            var unsigned = someUnsignedTransaction();
            var signed = someSignedTransaction();
            var broadcastResult = someBroadcastResult();

            given(activities.acquireResource(eqIgnoring(SOME_SEQUENTIAL_INTENT, "amount", "rawAmount"))).willReturn(resource);
            given(activities.build(eqIgnoring(SOME_SEQUENTIAL_INTENT, "amount", "rawAmount"), eqIgnoring(resource))).willReturn(unsigned);
            given(activities.sign(eqIgnoring(unsigned, "payload"), eqIgnoring(SOME_FROM_ADDRESS))).willReturn(signed);
            given(activities.broadcast(eqIgnoring(signed, "signedPayload"), eqIgnoring(SOME_CHAIN))).willReturn(broadcastResult);
            given(activities.checkStatus(SOME_TX_HASH, SOME_CHAIN)).willReturn(PENDING);

            testEnv.start();
            var workflow = startWorkflow(SOME_SEQUENTIAL_INTENT);
            var stub = WorkflowStub.fromTyped(workflow);

            // when
            WorkflowClient.start(workflow::process, SOME_SEQUENTIAL_INTENT, null);

            testEnv.registerDelayedCallback(Duration.ofSeconds(1), () -> {
                var snapshot = workflow.getStatus();

                // then
                var expected = TransactionSnapshot.builder()
                        .transactionId(snapshot.transactionId())
                        .intentId(SOME_INTENT_ID)
                        .status(PENDING)
                        .txHash(SOME_TX_HASH)
                        .retryCount(0)
                        .gasSpent(BigDecimal.ZERO)
                        .updatedAt(snapshot.updatedAt())
                        .build();
                assertThat(snapshot)
                        .usingRecursiveComparison()
                        .ignoringFields("transactionId", "updatedAt")
                        .isEqualTo(expected);

                workflow.cancelTransaction(CancelRequest.builder()
                        .requestedBy("test").build());
            });

            stub.getResult(TransactionResult.class);
        }
    }

    @Nested
    class HumanAbort {

        @Test
        void shouldFailOnHumanAbort() {
            // given
            var resource = someEvmResource();
            var unsigned = someUnsignedTransaction();
            var signed = someSignedTransaction();
            var broadcastResult = someBroadcastResult();
            var assessment = StuckAssessment.builder()
                    .reason(StuckReason.UNDERPRICED)
                    .severity(StuckSeverity.HIGH)
                    .recommendedPlan(someSpeedUpPlan(SOME_TX_HASH))
                    .explanation("Gas price too low")
                    .build();

            given(activities.acquireResource(eqIgnoring(SOME_SEQUENTIAL_INTENT, "amount", "rawAmount"))).willReturn(resource);
            given(activities.build(eqIgnoring(SOME_SEQUENTIAL_INTENT, "amount", "rawAmount"), eqIgnoring(resource))).willReturn(unsigned);
            given(activities.sign(eqIgnoring(unsigned, "payload"), eqIgnoring(SOME_FROM_ADDRESS))).willReturn(signed);
            given(activities.broadcast(eqIgnoring(signed, "signedPayload"), eqIgnoring(SOME_CHAIN))).willReturn(broadcastResult);
            given(activities.checkStatus(SOME_TX_HASH, SOME_CHAIN)).willReturn(STUCK);
            given(activities.assessStuck(eqIgnoring(buildExpectedSubmitted(), "submittedAt", "gasSpent", "gasBudget", "transactionId", "currentTier", "retryCount"))).willReturn(assessment);

            testEnv.start();
            var workflow = startWorkflow(SOME_SEQUENTIAL_INTENT);
            var stub = WorkflowStub.fromTyped(workflow);

            // when
            WorkflowClient.start(workflow::process, SOME_SEQUENTIAL_INTENT, null);

            testEnv.registerDelayedCallback(
                    Duration.ofSeconds(5),
                    () -> workflow.approveRecovery(HumanApproval.builder()
                            .action(ApprovalAction.ABORT)
                            .approvedBy("admin-1")
                            .reason("abort requested")
                            .approvedAt(Instant.parse("2026-01-01T01:00:00Z"))
                            .build()));

            var result = stub.getResult(TransactionResult.class);

            // then
            var expected = TransactionResult.builder()
                    .transactionId(result.transactionId())
                    .intentId(SOME_INTENT_ID)
                    .finalStatus(FAILED)
                    .txHash(SOME_TX_HASH)
                    .totalGasSpent(BigDecimal.ZERO)
                    .gasDenomination("ETH")
                    .totalAttempts(0)
                    .completedAt(result.completedAt())
                    .build();
            assertThat(result)
                    .usingRecursiveComparison()
                    .ignoringFields("transactionId", "completedAt", "totalAttempts", "gasDenomination")
                    .isEqualTo(expected);
            then(activities).should(atLeastOnce()).releaseResource(eqIgnoring(resource));
            then(activities).should(never()).consumeResource(eqIgnoring(resource));
        }
    }

    @Nested
    class ConfirmationFinality {

        @Test
        void shouldContinueMonitoringWhenNotYetFinalized() {
            // given
            var resource = someEvmResource();
            var unsigned = someUnsignedTransaction();
            var signed = someSignedTransaction();
            var broadcastResult = someBroadcastResult();

            given(activities.acquireResource(eqIgnoring(SOME_SEQUENTIAL_INTENT, "amount", "rawAmount"))).willReturn(resource);
            given(activities.build(eqIgnoring(SOME_SEQUENTIAL_INTENT, "amount", "rawAmount"), eqIgnoring(resource))).willReturn(unsigned);
            given(activities.sign(eqIgnoring(unsigned, "payload"), eqIgnoring(SOME_FROM_ADDRESS))).willReturn(signed);
            given(activities.broadcast(eqIgnoring(signed, "signedPayload"), eqIgnoring(SOME_CHAIN))).willReturn(broadcastResult);
            given(activities.checkStatus(SOME_TX_HASH, SOME_CHAIN)).willReturn(CONFIRMED);

            var finalityCount = new AtomicInteger(0);
            given(activities.waitForFinality(SOME_TX_HASH, SOME_CHAIN))
                    .willAnswer(_ -> finalityCount.incrementAndGet() == 1
                            ? someNotFinalizedConfirmation()
                            : someFinalizedConfirmation());

            // when
            testEnv.start();
            var workflow = startWorkflow(SOME_SEQUENTIAL_INTENT);
            var result = workflow.process(SOME_SEQUENTIAL_INTENT, null);

            // then
            var expected = TransactionResult.builder()
                    .transactionId(result.transactionId())
                    .intentId(SOME_INTENT_ID)
                    .finalStatus(FINALIZED)
                    .txHash(SOME_TX_HASH)
                    .totalGasSpent(BigDecimal.ZERO)
                    .gasDenomination("ETH")
                    .totalAttempts(0)
                    .completedAt(result.completedAt())
                    .build();
            assertThat(result)
                    .usingRecursiveComparison()
                    .isEqualTo(expected);

            then(activities).should(times(2)).waitForFinality(SOME_TX_HASH, SOME_CHAIN);
            then(activities).should().consumeResource(eqIgnoring(resource));
        }
    }

    @Nested
    class HumanCancel {

        @Test
        void shouldReleaseResourceOnHumanCancel() {
            // given
            var resource = someEvmResource();
            var unsigned = someUnsignedTransaction();
            var signed = someSignedTransaction();
            var broadcastResult = someBroadcastResult();
            var assessment = StuckAssessment.builder()
                    .reason(StuckReason.UNDERPRICED)
                    .severity(StuckSeverity.HIGH)
                    .recommendedPlan(someSpeedUpPlan(SOME_TX_HASH))
                    .explanation("Gas price too low")
                    .build();

            given(activities.acquireResource(eqIgnoring(SOME_SEQUENTIAL_INTENT, "amount", "rawAmount"))).willReturn(resource);
            given(activities.build(eqIgnoring(SOME_SEQUENTIAL_INTENT, "amount", "rawAmount"), eqIgnoring(resource))).willReturn(unsigned);
            given(activities.sign(eqIgnoring(unsigned, "payload"), eqIgnoring(SOME_FROM_ADDRESS))).willReturn(signed);
            given(activities.broadcast(eqIgnoring(signed, "signedPayload"), eqIgnoring(SOME_CHAIN))).willReturn(broadcastResult);
            given(activities.checkStatus(SOME_TX_HASH, SOME_CHAIN)).willReturn(STUCK);
            given(activities.assessStuck(eqIgnoring(buildExpectedSubmitted(), "submittedAt", "gasSpent", "gasBudget", "transactionId", "currentTier", "retryCount"))).willReturn(assessment);

            testEnv.start();
            var workflow = startWorkflow(SOME_SEQUENTIAL_INTENT);
            var stub = WorkflowStub.fromTyped(workflow);

            // when
            WorkflowClient.start(workflow::process, SOME_SEQUENTIAL_INTENT, null);

            testEnv.registerDelayedCallback(
                    Duration.ofSeconds(5),
                    () -> workflow.approveRecovery(HumanApproval.builder()
                            .action(ApprovalAction.CANCEL)
                            .approvedBy("admin-1")
                            .reason("cancel requested")
                            .approvedAt(Instant.parse("2026-01-01T01:00:00Z"))
                            .build()));

            var result = stub.getResult(TransactionResult.class);

            // then
            var expected = TransactionResult.builder()
                    .transactionId(result.transactionId())
                    .intentId(SOME_INTENT_ID)
                    .finalStatus(CANCELLED)
                    .txHash(SOME_TX_HASH)
                    .totalGasSpent(BigDecimal.ZERO)
                    .gasDenomination("ETH")
                    .totalAttempts(0)
                    .completedAt(result.completedAt())
                    .build();
            assertThat(result)
                    .usingRecursiveComparison()
                    .ignoringFields("transactionId", "completedAt", "totalAttempts", "gasDenomination")
                    .isEqualTo(expected);
            then(activities).should(atLeastOnce()).releaseResource(eqIgnoring(resource));
            then(activities).should(never()).consumeResource(eqIgnoring(resource));
        }
    }

    @Nested
    class MidFlightFailure {

        @Test
        void shouldReleaseResourceOnBuildFailure() {
            // given
            var resource = someEvmResource();

            given(activities.acquireResource(eqIgnoring(SOME_SEQUENTIAL_INTENT, "amount", "rawAmount"))).willReturn(resource);
            given(activities.build(eqIgnoring(SOME_SEQUENTIAL_INTENT, "amount", "rawAmount"), eqIgnoring(resource)))
                    .willThrow(new NonRetryableException("build failed"));

            // when
            testEnv.start();
            var workflow = startWorkflow(SOME_SEQUENTIAL_INTENT);
            var result = workflow.process(SOME_SEQUENTIAL_INTENT, null);

            // then
            assertThat(result.finalStatus()).isEqualTo(FAILED);
            then(activities).should().releaseResource(eqIgnoring(resource));
            then(activities).should(never()).consumeResource(eqIgnoring(resource));
        }
    }

    @Nested
    class RetryResetsCount {

        @Test
        void shouldAllowAutomaticRecoveryAfterHumanRetry() {
            // given
            var resource = someEvmResource();
            var unsigned = someUnsignedTransaction();
            var signed = someSignedTransaction();
            var broadcastResult = someBroadcastResult();
            var speedUpPlan = someSpeedUpPlan(SOME_TX_HASH);
            var assessment = StuckAssessment.builder()
                    .reason(StuckReason.UNDERPRICED)
                    .severity(StuckSeverity.MEDIUM)
                    .recommendedPlan(speedUpPlan)
                    .explanation("Gas price too low")
                    .build();
            var recoveryResult = RecoveryResult.builder()
                    .outcome(RecoveryOutcome.REPLACEMENT_SUBMITTED)
                    .replacementTxHash(SOME_TX_HASH)
                    .gasCost(new BigDecimal("0.001"))
                    .build();
            var confirmation = someFinalizedConfirmation();

            given(activities.acquireResource(eqIgnoring(SOME_SEQUENTIAL_INTENT, "amount", "rawAmount"))).willReturn(resource);
            given(activities.build(eqIgnoring(SOME_SEQUENTIAL_INTENT, "amount", "rawAmount"), eqIgnoring(resource))).willReturn(unsigned);
            given(activities.sign(eqIgnoring(unsigned, "payload"), eqIgnoring(SOME_FROM_ADDRESS))).willReturn(signed);
            given(activities.broadcast(eqIgnoring(signed, "signedPayload"), eqIgnoring(SOME_CHAIN))).willReturn(broadcastResult);
            given(activities.assessStuck(eqIgnoring(buildExpectedSubmitted(), "submittedAt", "gasSpent", "gasBudget", "transactionId", "currentTier", "retryCount"))).willReturn(assessment);
            given(activities.executeRecovery(eqIgnoring(speedUpPlan), eqIgnoring(SOME_CHAIN))).willReturn(recoveryResult);
            given(activities.waitForFinality(SOME_TX_HASH, SOME_CHAIN)).willReturn(confirmation);

            var checkCount = new AtomicInteger(0);
            given(activities.checkStatus(SOME_TX_HASH, SOME_CHAIN))
                    .willAnswer(_ -> {
                        var count = checkCount.incrementAndGet();
                        if (count <= 3) return STUCK;
                        if (count == 4) return STUCK;
                        return CONFIRMED;
                    });

            testEnv.start();
            var workflow = startWorkflow(SOME_SEQUENTIAL_INTENT);
            var stub = WorkflowStub.fromTyped(workflow);

            // when
            WorkflowClient.start(workflow::process, SOME_SEQUENTIAL_INTENT, null);

            testEnv.registerDelayedCallback(
                    Duration.ofSeconds(5),
                    () -> workflow.approveRecovery(HumanApproval.builder()
                            .action(ApprovalAction.RETRY)
                            .approvedBy("admin-1")
                            .reason("approved")
                            .approvedAt(Instant.parse("2026-01-01T01:00:00Z"))
                            .build()));

            var result = stub.getResult(TransactionResult.class);

            // then
            assertThat(result.finalStatus()).isEqualTo(FINALIZED);
            then(activities).should(atLeastOnce()).assessStuck(
                    eqIgnoring(buildExpectedSubmitted(), "submittedAt", "gasSpent", "gasBudget", "transactionId", "currentTier", "retryCount"));
        }
    }

    @Nested
    class ApprovalTimeout {

        @Test
        void shouldAutoAbortAfterMaxApprovalTimeouts() {
            // given
            var resource = someEvmResource();
            var unsigned = someUnsignedTransaction();
            var signed = someSignedTransaction();
            var broadcastResult = someBroadcastResult();
            var assessment = StuckAssessment.builder()
                    .reason(StuckReason.UNDERPRICED)
                    .severity(StuckSeverity.HIGH)
                    .recommendedPlan(someSpeedUpPlan(SOME_TX_HASH))
                    .explanation("Gas price too low")
                    .build();

            given(activities.acquireResource(eqIgnoring(SOME_SEQUENTIAL_INTENT, "amount", "rawAmount"))).willReturn(resource);
            given(activities.build(eqIgnoring(SOME_SEQUENTIAL_INTENT, "amount", "rawAmount"), eqIgnoring(resource))).willReturn(unsigned);
            given(activities.sign(eqIgnoring(unsigned, "payload"), eqIgnoring(SOME_FROM_ADDRESS))).willReturn(signed);
            given(activities.broadcast(eqIgnoring(signed, "signedPayload"), eqIgnoring(SOME_CHAIN))).willReturn(broadcastResult);
            given(activities.checkStatus(SOME_TX_HASH, SOME_CHAIN)).willReturn(STUCK);
            given(activities.assessStuck(eqIgnoring(buildExpectedSubmitted(), "submittedAt", "gasSpent", "gasBudget", "transactionId", "currentTier", "retryCount"))).willReturn(assessment);

            // when
            testEnv.start();
            var workflow = startWorkflow(SOME_SEQUENTIAL_INTENT);
            var result = workflow.process(SOME_SEQUENTIAL_INTENT, null);

            // then
            var expected = TransactionResult.builder()
                    .transactionId(result.transactionId())
                    .intentId(SOME_INTENT_ID)
                    .finalStatus(FAILED)
                    .txHash(SOME_TX_HASH)
                    .totalGasSpent(BigDecimal.ZERO)
                    .gasDenomination("ETH")
                    .totalAttempts(0)
                    .completedAt(result.completedAt())
                    .build();
            assertThat(result)
                    .usingRecursiveComparison()
                    .ignoringFields("transactionId", "completedAt", "totalAttempts", "gasDenomination")
                    .isEqualTo(expected);

            var expectedApproval = HumanApproval.builder()
                    .action(ApprovalAction.ABORT)
                    .approvedBy("system")
                    .reason("HUMAN_RESPONSE_TIMEOUT")
                    .build();
            then(activities).should().recordApproval(
                    eqIgnoring(result.transactionId()),
                    eqIgnoring(expectedApproval, "approvedAt"));

            var eventCaptor = ArgumentCaptor.forClass(TransactionLifecycleEvent.class);
            then(activities).should(atLeastOnce()).publishEvent(eventCaptor.capture());
            var timeoutEvents = eventCaptor.getAllValues().stream()
                    .filter(e -> e.status() == AWAITING_HUMAN)
                    .filter(e -> e.metadata() != null && e.metadata().containsKey("timeout_count"))
                    .toList();
            assertThat(timeoutEvents).hasSize(3);
            assertThat(timeoutEvents.stream().map(e -> e.metadata().get("timeout_count")).toList())
                    .containsExactly("1", "2", "3");
            assertThat(timeoutEvents).allSatisfy(e -> {
                assertThat(e.intentId()).isEqualTo(SOME_INTENT_ID);
                assertThat(e.chain()).isEqualTo(SOME_CHAIN);
                assertThat(e.metadata().get("max_timeouts")).isEqualTo("3");
            });

            then(activities).should(atLeastOnce()).releaseResource(eqIgnoring(resource));
            then(activities).should(never()).consumeResource(eqIgnoring(resource));
        }
    }

    @Nested
    class ApprovalRecording {

        @Test
        void shouldRecordApprovalOnRetrySignal() {
            // given
            var resource = someEvmResource();
            var unsigned = someUnsignedTransaction();
            var signed = someSignedTransaction();
            var broadcastResult = someBroadcastResult();
            var assessment = StuckAssessment.builder()
                    .reason(StuckReason.UNDERPRICED)
                    .severity(StuckSeverity.HIGH)
                    .recommendedPlan(someSpeedUpPlan(SOME_TX_HASH))
                    .explanation("Gas price too low")
                    .build();
            var confirmation = someFinalizedConfirmation();

            given(activities.acquireResource(eqIgnoring(SOME_SEQUENTIAL_INTENT, "amount", "rawAmount"))).willReturn(resource);
            given(activities.build(eqIgnoring(SOME_SEQUENTIAL_INTENT, "amount", "rawAmount"), eqIgnoring(resource))).willReturn(unsigned);
            given(activities.sign(eqIgnoring(unsigned, "payload"), eqIgnoring(SOME_FROM_ADDRESS))).willReturn(signed);
            given(activities.broadcast(eqIgnoring(signed, "signedPayload"), eqIgnoring(SOME_CHAIN))).willReturn(broadcastResult);
            var checkCount = new AtomicInteger(0);
            given(activities.checkStatus(SOME_TX_HASH, SOME_CHAIN))
                    .willAnswer(_ -> checkCount.incrementAndGet() <= 3 ? STUCK : CONFIRMED);
            given(activities.assessStuck(eqIgnoring(buildExpectedSubmitted(), "submittedAt", "gasSpent", "gasBudget", "transactionId", "currentTier", "retryCount"))).willReturn(assessment);
            given(activities.waitForFinality(SOME_TX_HASH, SOME_CHAIN)).willReturn(confirmation);

            testEnv.start();
            var workflow = startWorkflow(SOME_SEQUENTIAL_INTENT);
            var stub = WorkflowStub.fromTyped(workflow);

            // when
            WorkflowClient.start(workflow::process, SOME_SEQUENTIAL_INTENT, null);

            testEnv.registerDelayedCallback(
                    Duration.ofSeconds(5),
                    () -> workflow.approveRecovery(HumanApproval.builder()
                            .action(ApprovalAction.RETRY)
                            .approvedBy("admin-1")
                            .reason("approved")
                            .approvedAt(Instant.parse("2026-01-01T01:00:00Z"))
                            .build()));

            var result = stub.getResult(TransactionResult.class);

            // then
            var expectedApproval = HumanApproval.builder()
                    .action(ApprovalAction.RETRY)
                    .approvedBy("admin-1")
                    .reason("approved")
                    .build();
            then(activities).should().recordApproval(
                    eqIgnoring(result.transactionId()),
                    eqIgnoring(expectedApproval, "approvedAt"));
        }

        @Test
        void shouldRecordApprovalOnAbortSignal() {
            // given
            var resource = someEvmResource();
            var unsigned = someUnsignedTransaction();
            var signed = someSignedTransaction();
            var broadcastResult = someBroadcastResult();
            var assessment = StuckAssessment.builder()
                    .reason(StuckReason.UNDERPRICED)
                    .severity(StuckSeverity.HIGH)
                    .recommendedPlan(someSpeedUpPlan(SOME_TX_HASH))
                    .explanation("Gas price too low")
                    .build();

            given(activities.acquireResource(eqIgnoring(SOME_SEQUENTIAL_INTENT, "amount", "rawAmount"))).willReturn(resource);
            given(activities.build(eqIgnoring(SOME_SEQUENTIAL_INTENT, "amount", "rawAmount"), eqIgnoring(resource))).willReturn(unsigned);
            given(activities.sign(eqIgnoring(unsigned, "payload"), eqIgnoring(SOME_FROM_ADDRESS))).willReturn(signed);
            given(activities.broadcast(eqIgnoring(signed, "signedPayload"), eqIgnoring(SOME_CHAIN))).willReturn(broadcastResult);
            given(activities.checkStatus(SOME_TX_HASH, SOME_CHAIN)).willReturn(STUCK);
            given(activities.assessStuck(eqIgnoring(buildExpectedSubmitted(), "submittedAt", "gasSpent", "gasBudget", "transactionId", "currentTier", "retryCount"))).willReturn(assessment);

            testEnv.start();
            var workflow = startWorkflow(SOME_SEQUENTIAL_INTENT);
            var stub = WorkflowStub.fromTyped(workflow);

            // when
            WorkflowClient.start(workflow::process, SOME_SEQUENTIAL_INTENT, null);

            testEnv.registerDelayedCallback(
                    Duration.ofSeconds(5),
                    () -> workflow.approveRecovery(HumanApproval.builder()
                            .action(ApprovalAction.ABORT)
                            .approvedBy("admin-1")
                            .reason("abort requested")
                            .approvedAt(Instant.parse("2026-01-01T01:00:00Z"))
                            .build()));

            var result = stub.getResult(TransactionResult.class);

            // then
            var expectedApproval = HumanApproval.builder()
                    .action(ApprovalAction.ABORT)
                    .approvedBy("admin-1")
                    .reason("abort requested")
                    .build();
            then(activities).should().recordApproval(
                    eqIgnoring(result.transactionId()),
                    eqIgnoring(expectedApproval, "approvedAt"));
            then(activities).should(atLeastOnce()).releaseResource(eqIgnoring(resource));
            then(activities).should(never()).consumeResource(eqIgnoring(resource));
        }
    }

    @Nested
    class WorkflowId {

        @Test
        void shouldGenerateWorkflowIdFromIntentId() {
            // when
            var workflowId = TransactionLifecycleWorkflow.workflowId("intent-123");

            // then
            assertThat(workflowId).isEqualTo("str-tx-intent-123");
        }
    }

    @Nested
    class ContinueAsNew {

        @Test
        void shouldResumeFromPreviousStateAfterContinueAsNew() {
            // given
            var previousState = someContinueAsNewState();
            var confirmation = someFinalizedConfirmation();

            given(activities.checkStatus(SOME_TX_HASH, SOME_CHAIN)).willReturn(CONFIRMED);
            given(activities.waitForFinality(SOME_TX_HASH, SOME_CHAIN)).willReturn(confirmation);

            // when
            testEnv.start();
            var workflow = startWorkflow(SOME_SEQUENTIAL_INTENT);
            var result = workflow.process(SOME_SEQUENTIAL_INTENT, previousState);

            // then
            var expected = TransactionResult.builder()
                    .transactionId(previousState.transactionId())
                    .intentId(SOME_INTENT_ID)
                    .finalStatus(FINALIZED)
                    .txHash(SOME_TX_HASH)
                    .totalGasSpent(previousState.totalGasSpent())
                    .gasDenomination("ETH")
                    .totalAttempts(previousState.retryCount())
                    .completedAt(result.completedAt())
                    .build();
            assertThat(result)
                    .usingRecursiveComparison()
                    .isEqualTo(expected);

            then(activities).should().consumeResource(eqIgnoring(someEvmResource()));
            then(activities).should(never()).acquireResource(eqIgnoring(SOME_SEQUENTIAL_INTENT, "amount", "rawAmount"));
        }

        @Test
        void shouldResumeWithPendingApprovalAndProcessImmediately() {
            // given
            var approval = HumanApproval.builder()
                    .action(ApprovalAction.RETRY)
                    .approvedBy("admin-1")
                    .reason("retry approved")
                    .approvedAt(Instant.parse("2026-01-01T01:00:00Z"))
                    .build();
            var previousState = someContinueAsNewState().toBuilder()
                    .retryCount(3)
                    .pendingApproval(approval)
                    .currentState(PENDING)
                    .build();
            var assessment = StuckAssessment.builder()
                    .reason(StuckReason.UNDERPRICED)
                    .severity(StuckSeverity.HIGH)
                    .recommendedPlan(someSpeedUpPlan(SOME_TX_HASH))
                    .explanation("Gas price too low")
                    .build();
            var confirmation = someFinalizedConfirmation();

            given(activities.checkStatus(SOME_TX_HASH, SOME_CHAIN))
                    .willReturn(STUCK)
                    .willReturn(CONFIRMED);
            given(activities.assessStuck(eqIgnoring(buildExpectedSubmitted(),
                    "submittedAt", "gasSpent", "gasBudget", "transactionId", "currentTier", "retryCount")))
                    .willReturn(assessment);
            given(activities.waitForFinality(SOME_TX_HASH, SOME_CHAIN)).willReturn(confirmation);

            // when
            testEnv.start();
            var workflow = startWorkflow(SOME_SEQUENTIAL_INTENT);
            var result = workflow.process(SOME_SEQUENTIAL_INTENT, previousState);

            // then
            assertThat(result.finalStatus()).isEqualTo(FINALIZED);
            then(activities).should(never()).acquireResource(eqIgnoring(SOME_SEQUENTIAL_INTENT, "amount", "rawAmount"));
            then(activities).should().recordApproval(
                    eqIgnoring(previousState.transactionId()),
                    eqIgnoring(approval, "approvedAt"));
        }

        @Test
        void shouldPreserveCancelSignalStateAcrossContinueAsNew() {
            // given
            var cancelRequest = CancelRequest.builder()
                    .requestedBy("operator-1")
                    .reason("test cancel")
                    .requestedAt(Instant.parse("2026-01-01T00:00:00Z"))
                    .build();
            var previousState = someContinueAsNewState().toBuilder()
                    .cancelRequested(true)
                    .cancelRequest(cancelRequest)
                    .build();

            // when
            testEnv.start();
            var workflow = startWorkflow(SOME_SEQUENTIAL_INTENT);
            var result = workflow.process(SOME_SEQUENTIAL_INTENT, previousState);

            // then
            assertThat(result.finalStatus()).isEqualTo(CANCELLED);
            then(activities).should(never()).acquireResource(eqIgnoring(SOME_SEQUENTIAL_INTENT, "amount", "rawAmount"));
        }
    }

    private TransactionLifecycleWorkflow startWorkflow(TransactionIntent intent) {
        var workflowId = TransactionLifecycleWorkflow.workflowId(intent.intentId());
        return client.newWorkflowStub(
                TransactionLifecycleWorkflow.class,
                WorkflowOptions.newBuilder()
                        .setWorkflowId(workflowId)
                        .setTaskQueue(TASK_QUEUE)
                        .build());
    }

    private static SubmittedTransaction buildExpectedSubmitted() {
        return SubmittedTransaction.builder()
                .transactionId("ignored")
                .intentId(SOME_INTENT_ID)
                .chain(SOME_CHAIN)
                .txHash(SOME_TX_HASH)
                .fromAddress(SOME_FROM_ADDRESS)
                .resource(someEvmResource())
                .status(STUCK)
                .retryCount(0)
                .gasSpent(BigDecimal.ZERO)
                .gasBudget(BigDecimal.ZERO)
                .build();
    }
}
