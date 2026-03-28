package com.stablebridge.txrecovery.application.workflow;

import static com.stablebridge.txrecovery.testutil.TestUtils.eqIgnoring;
import static com.stablebridge.txrecovery.testutil.fixtures.TransactionIntentFixtures.*;
import static com.stablebridge.txrecovery.testutil.fixtures.WorkflowTestFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.stablebridge.txrecovery.domain.address.model.ChainFamily;
import com.stablebridge.txrecovery.domain.recovery.model.EscalationPolicy;
import com.stablebridge.txrecovery.domain.recovery.model.EscalationTier;
import com.stablebridge.txrecovery.domain.recovery.model.GasBudgetPolicy;
import com.stablebridge.txrecovery.domain.recovery.model.RecoveryOutcome;
import com.stablebridge.txrecovery.domain.recovery.model.RecoveryPlan;
import com.stablebridge.txrecovery.domain.recovery.model.RecoveryResult;
import com.stablebridge.txrecovery.domain.recovery.model.StuckAssessment;
import com.stablebridge.txrecovery.domain.recovery.model.StuckReason;
import com.stablebridge.txrecovery.domain.recovery.model.StuckSeverity;
import com.stablebridge.txrecovery.domain.recovery.port.RecoveryStrategy;
import com.stablebridge.txrecovery.domain.transaction.event.TransactionLifecycleEvent;
import com.stablebridge.txrecovery.domain.transaction.model.TransactionStatus;
import com.stablebridge.txrecovery.domain.transaction.port.ChainTransactionManager;
import com.stablebridge.txrecovery.domain.transaction.port.SubmissionResourceManager;
import com.stablebridge.txrecovery.domain.transaction.port.TransactionEventPublisher;
import com.stablebridge.txrecovery.domain.transaction.port.TransactionSigner;

import io.temporal.testing.TestActivityEnvironment;

class TransactionLifecycleActivitiesImplTest {

    private static final Duration DEFAULT_POLL_INTERVAL = Duration.ofSeconds(15);
    private static final GasBudgetPolicy GAS_BUDGET_POLICY = GasBudgetPolicy.builder()
            .percentage(new BigDecimal("0.05"))
            .absoluteMinUsd(new BigDecimal("5"))
            .absoluteMaxUsd(new BigDecimal("500"))
            .build();
    private static final EscalationTier TIER_0 = EscalationTier.builder()
            .level(0).stuckThreshold(Duration.ofMinutes(5))
            .gasMultiplier(new BigDecimal("1.5")).requiresHumanApproval(false).build();
    private static final EscalationTier TIER_1 = EscalationTier.builder()
            .level(1).stuckThreshold(Duration.ofMinutes(15))
            .gasMultiplier(new BigDecimal("2.0")).requiresHumanApproval(false).build();
    private static final EscalationTier TIER_2 = EscalationTier.builder()
            .level(2).stuckThreshold(Duration.ofMinutes(30))
            .gasMultiplier(new BigDecimal("3.0")).requiresHumanApproval(true).build();
    private static final EscalationPolicy ESCALATION_POLICY = EscalationPolicy.builder()
            .tiers(List.of(TIER_0, TIER_1, TIER_2))
            .build();
    private static final Map<String, ChainFamily> CHAIN_FAMILY_MAPPING = Map.of(
            SOME_CHAIN, ChainFamily.EVM,
            "solana-mainnet", ChainFamily.SOLANA);

    private ChainTransactionManager evmChainTransactionManager;
    private SubmissionResourceManager evmSubmissionResourceManager;
    private RecoveryStrategy evmRecoveryStrategy;
    private TransactionSigner transactionSigner;
    private TransactionEventPublisher eventPublisher;

    private TestActivityEnvironment testActivityEnv;
    private TransactionLifecycleActivities activities;

    @BeforeEach
    void setUp() {
        evmChainTransactionManager = mock(ChainTransactionManager.class);
        evmSubmissionResourceManager = mock(SubmissionResourceManager.class);
        evmRecoveryStrategy = mock(RecoveryStrategy.class);
        transactionSigner = mock(TransactionSigner.class);
        eventPublisher = mock(TransactionEventPublisher.class);

        given(evmRecoveryStrategy.appliesTo(ChainFamily.EVM)).willReturn(true);
        given(evmRecoveryStrategy.appliesTo(ChainFamily.SOLANA)).willReturn(false);

        var impl = new TransactionLifecycleActivitiesImpl(
                Map.of(ChainFamily.EVM, evmChainTransactionManager),
                Map.of(ChainFamily.EVM, evmSubmissionResourceManager),
                List.of(evmRecoveryStrategy),
                transactionSigner,
                eventPublisher,
                GAS_BUDGET_POLICY,
                ESCALATION_POLICY,
                CHAIN_FAMILY_MAPPING,
                DEFAULT_POLL_INTERVAL);

        testActivityEnv = TestActivityEnvironment.newInstance();
        testActivityEnv.registerActivitiesImplementations(impl);
        activities = testActivityEnv.newActivityStub(TransactionLifecycleActivities.class);
    }

    @AfterEach
    void tearDown() {
        testActivityEnv.close();
    }

    @Nested
    class AcquireResource {

        @Test
        void shouldDelegateToCorrectResourceManager() {
            // given
            var resource = someEvmResource();
            given(evmSubmissionResourceManager.acquire(eqIgnoring(SOME_SEQUENTIAL_INTENT, "amount", "rawAmount")))
                    .willReturn(resource);

            // when
            var result = activities.acquireResource(SOME_SEQUENTIAL_INTENT);

            // then
            assertThat(result)
                    .usingRecursiveComparison()
                    .isEqualTo(resource);
        }

        @Test
        void shouldThrowForUnmappedChain() {
            // given
            var unknownChainIntent = SOME_SEQUENTIAL_INTENT.toBuilder()
                    .chain("unknown-chain")
                    .build();

            // when / then
            assertThatThrownBy(() -> activities.acquireResource(unknownChainIntent))
                    .hasMessageContaining("No chain family mapping found for chain: unknown-chain");
        }
    }

    @Nested
    class ReleaseResource {

        @Test
        void shouldDelegateToCorrectResourceManagerForEvm() {
            // given
            var resource = someEvmResource();

            // when
            activities.releaseResource(resource);

            // then
            then(evmSubmissionResourceManager).should().release(eqIgnoring(resource));
        }
    }

    @Nested
    class ConsumeResource {

        @Test
        void shouldDelegateToCorrectResourceManagerForEvm() {
            // given
            var resource = someEvmResource();

            // when
            activities.consumeResource(resource);

            // then
            then(evmSubmissionResourceManager).should().consume(eqIgnoring(resource));
        }
    }

    @Nested
    class Build {

        @Test
        void shouldDelegateToChainTransactionManager() {
            // given
            var resource = someEvmResource();
            var unsigned = someUnsignedTransaction();
            given(evmChainTransactionManager.build(
                    eqIgnoring(SOME_SEQUENTIAL_INTENT, "amount", "rawAmount"), eqIgnoring(resource)))
                    .willReturn(unsigned);

            // when
            var result = activities.build(SOME_SEQUENTIAL_INTENT, resource);

            // then
            assertThat(result)
                    .usingRecursiveComparison()
                    .ignoringFields("payload")
                    .isEqualTo(unsigned);
        }
    }

    @Nested
    class Sign {

        @Test
        void shouldDelegateToTransactionSigner() {
            // given
            var unsigned = someUnsignedTransaction();
            var signed = someSignedTransaction();
            given(transactionSigner.sign(eqIgnoring(unsigned, "payload"), eqIgnoring(SOME_FROM_ADDRESS)))
                    .willReturn(signed);

            // when
            var result = activities.sign(unsigned, SOME_FROM_ADDRESS);

            // then
            assertThat(result)
                    .usingRecursiveComparison()
                    .ignoringFields("signedPayload")
                    .isEqualTo(signed);
        }
    }

    @Nested
    class Broadcast {

        @Test
        void shouldDelegateToChainTransactionManager() {
            // given
            var signed = someSignedTransaction();
            var broadcastResult = someBroadcastResult();
            given(evmChainTransactionManager.broadcast(eqIgnoring(signed, "signedPayload"), eqIgnoring(SOME_CHAIN)))
                    .willReturn(broadcastResult);

            // when
            var result = activities.broadcast(signed, SOME_CHAIN);

            // then
            assertThat(result)
                    .usingRecursiveComparison()
                    .isEqualTo(broadcastResult);
        }
    }

    @Nested
    class CheckStatus {

        @Test
        void shouldDelegateToChainTransactionManager() {
            // given
            given(evmChainTransactionManager.checkStatus(SOME_TX_HASH, SOME_CHAIN))
                    .willReturn(TransactionStatus.CONFIRMED);

            // when
            var result = activities.checkStatus(SOME_TX_HASH, SOME_CHAIN);

            // then
            assertThat(result).isEqualTo(TransactionStatus.CONFIRMED);
        }
    }

    @Nested
    class WaitForFinality {

        @Test
        void shouldDelegateAndReturnConfirmationStatus() {
            // given
            var confirmation = someFinalizedConfirmation();
            given(evmChainTransactionManager.getConfirmationStatus(SOME_TX_HASH, SOME_CHAIN))
                    .willReturn(confirmation);

            // when
            var result = activities.waitForFinality(SOME_TX_HASH, SOME_CHAIN);

            // then
            assertThat(result)
                    .usingRecursiveComparison()
                    .isEqualTo(confirmation);
        }

        @Test
        void shouldReturnNotFinalizedWhenConfirmationsInsufficient() {
            // given
            var notFinalized = someNotFinalizedConfirmation();
            given(evmChainTransactionManager.getConfirmationStatus(SOME_TX_HASH, SOME_CHAIN))
                    .willReturn(notFinalized);

            // when
            var result = activities.waitForFinality(SOME_TX_HASH, SOME_CHAIN);

            // then
            assertThat(result)
                    .usingRecursiveComparison()
                    .isEqualTo(notFinalized);
        }
    }

    @Nested
    class GetPollInterval {

        @Test
        void shouldReturnDefaultPollInterval() {
            // when
            var result = activities.getPollInterval(SOME_CHAIN);

            // then
            assertThat(result).isEqualTo(DEFAULT_POLL_INTERVAL);
        }
    }

    @Nested
    class CalculateGasBudget {

        @Test
        void shouldDelegateToGasBudgetPolicy() {
            // given
            var txValue = new BigDecimal("1000");

            // when
            var result = activities.calculateGasBudget(txValue);

            // then
            var expected = GAS_BUDGET_POLICY.calculateBudget(txValue);
            assertThat(result).isEqualByComparingTo(expected);
        }

        @Test
        void shouldEnforceMinimumBudget() {
            // given
            var smallTxValue = new BigDecimal("10");

            // when
            var result = activities.calculateGasBudget(smallTxValue);

            // then
            assertThat(result).isEqualByComparingTo(new BigDecimal("5"));
        }

        @Test
        void shouldEnforceMaximumBudget() {
            // given
            var largeTxValue = new BigDecimal("100000");

            // when
            var result = activities.calculateGasBudget(largeTxValue);

            // then
            assertThat(result).isEqualByComparingTo(new BigDecimal("500"));
        }
    }

    @Nested
    class AssessStuck {

        @Test
        void shouldDelegateToCorrectRecoveryStrategy() {
            // given
            var submitted = someSubmittedTransaction();
            var assessment = StuckAssessment.builder()
                    .reason(StuckReason.UNDERPRICED)
                    .severity(StuckSeverity.MEDIUM)
                    .recommendedPlan(someSpeedUpPlan(SOME_TX_HASH))
                    .explanation("Gas price too low")
                    .build();
            given(evmRecoveryStrategy.assess(eqIgnoring(submitted, "gasSpent", "gasBudget")))
                    .willReturn(assessment);

            // when
            var result = activities.assessStuck(submitted);

            // then
            assertThat(result)
                    .usingRecursiveComparison()
                    .isEqualTo(assessment);
        }
    }

    @Nested
    class DetermineEscalationTier {

        @Test
        void shouldReturnTier0ForShortStuckDuration() {
            // when
            var result = activities.determineEscalationTier(Duration.ofMinutes(6));

            // then
            assertThat(result)
                    .usingRecursiveComparison()
                    .isEqualTo(TIER_0);
        }

        @Test
        void shouldReturnTier1ForMediumStuckDuration() {
            // when
            var result = activities.determineEscalationTier(Duration.ofMinutes(20));

            // then
            assertThat(result)
                    .usingRecursiveComparison()
                    .isEqualTo(TIER_1);
        }

        @Test
        void shouldReturnTier2ForLongStuckDuration() {
            // when
            var result = activities.determineEscalationTier(Duration.ofMinutes(45));

            // then
            assertThat(result)
                    .usingRecursiveComparison()
                    .isEqualTo(TIER_2);
        }

        @Test
        void shouldReturnFirstTierWhenBelowAllThresholds() {
            // when
            var result = activities.determineEscalationTier(Duration.ofMinutes(1));

            // then
            assertThat(result)
                    .usingRecursiveComparison()
                    .isEqualTo(TIER_0);
        }

        @Test
        void shouldReturnExactThresholdMatch() {
            // when
            var result = activities.determineEscalationTier(Duration.ofMinutes(15));

            // then
            assertThat(result)
                    .usingRecursiveComparison()
                    .isEqualTo(TIER_1);
        }
    }

    @Nested
    class ExecuteRecovery {

        @Test
        void shouldDelegateToCorrectRecoveryStrategy() {
            // given
            var plan = someSpeedUpPlan(SOME_TX_HASH);
            var recoveryResult = RecoveryResult.builder()
                    .outcome(RecoveryOutcome.REPLACEMENT_SUBMITTED)
                    .replacementTxHash("0xreplacement")
                    .gasCost(new BigDecimal("0.002"))
                    .build();
            given(evmRecoveryStrategy.execute(eqIgnoring(plan), eqIgnoring(transactionSigner)))
                    .willReturn(recoveryResult);

            // when
            var result = activities.executeRecovery(plan, SOME_CHAIN);

            // then
            assertThat(result)
                    .usingRecursiveComparison()
                    .isEqualTo(recoveryResult);
        }

        @Test
        void shouldThrowWhenChainIsUnmapped() {
            // given
            var plan = RecoveryPlan.SpeedUp.builder()
                    .originalTxHash("0xunknown")
                    .newFee(someFeeEstimate())
                    .build();

            // when / then
            assertThatThrownBy(() -> activities.executeRecovery(plan, "unknown-chain"))
                    .hasMessageContaining("No chain family mapping found for chain: unknown-chain");
        }
    }

    @Nested
    class CancelOnChain {

        @Test
        void shouldCreateCancelPlanAndDelegateToStrategy() {
            // given
            var cancelPlan = RecoveryPlan.Cancel.builder().originalTxHash(SOME_TX_HASH).build();
            var cancelResult = RecoveryResult.builder()
                    .outcome(RecoveryOutcome.REPLACEMENT_SUBMITTED)
                    .replacementTxHash("0xcancel")
                    .gasCost(new BigDecimal("0.001"))
                    .build();
            given(evmRecoveryStrategy.execute(eqIgnoring(cancelPlan), eqIgnoring(transactionSigner)))
                    .willReturn(cancelResult);

            // when
            var result = activities.cancelOnChain(SOME_TX_HASH, SOME_CHAIN);

            // then
            assertThat(result)
                    .usingRecursiveComparison()
                    .isEqualTo(cancelResult);
        }
    }

    @Nested
    class PublishEvent {

        @Test
        void shouldDelegateToEventPublisher() {
            // given
            var event = TransactionLifecycleEvent.builder()
                    .eventId("evt-001")
                    .intentId(SOME_INTENT_ID)
                    .transactionHash(SOME_TX_HASH)
                    .chain(SOME_CHAIN)
                    .status(TransactionStatus.FINALIZED)
                    .timestamp(Instant.parse("2026-01-01T00:00:00Z"))
                    .build();

            // when
            activities.publishEvent(event);

            // then
            then(eventPublisher).should().publish(eqIgnoring(event));
        }
    }

    @Nested
    class ChainFamilyResolution {

        @Test
        void shouldResolveEvmFamilyFromResource() {
            // given
            var resource = someEvmResource();
            var unsigned = someUnsignedTransaction();
            given(evmChainTransactionManager.build(
                    eqIgnoring(SOME_SEQUENTIAL_INTENT, "amount", "rawAmount"), eqIgnoring(resource)))
                    .willReturn(unsigned);

            // when
            var result = activities.build(SOME_SEQUENTIAL_INTENT, resource);

            // then
            assertThat(result).isNotNull();
            then(evmChainTransactionManager).should().build(
                    eqIgnoring(SOME_SEQUENTIAL_INTENT, "amount", "rawAmount"), eqIgnoring(resource));
        }
    }
}
