package com.stablebridge.txrecovery.application.workflow;

import static com.stablebridge.txrecovery.domain.transaction.model.TransactionStatus.*;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;

import com.stablebridge.txrecovery.domain.recovery.model.ApprovalAction;
import com.stablebridge.txrecovery.domain.recovery.model.CancelRequest;
import com.stablebridge.txrecovery.domain.recovery.model.EscalationTier;
import com.stablebridge.txrecovery.domain.recovery.model.HumanApproval;
import com.stablebridge.txrecovery.domain.recovery.model.RecoveryOutcome;
import com.stablebridge.txrecovery.domain.recovery.model.RecoveryPlan;
import com.stablebridge.txrecovery.domain.transaction.event.TransactionLifecycleEvent;
import com.stablebridge.txrecovery.domain.transaction.model.SubmissionResource;
import com.stablebridge.txrecovery.domain.transaction.model.SubmittedTransaction;
import com.stablebridge.txrecovery.domain.transaction.model.TransactionIntent;
import com.stablebridge.txrecovery.domain.transaction.model.TransactionResult;
import com.stablebridge.txrecovery.domain.transaction.model.TransactionSnapshot;
import com.stablebridge.txrecovery.domain.transaction.model.TransactionStatus;

import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.workflow.Workflow;

public class TransactionLifecycleWorkflowImpl implements TransactionLifecycleWorkflow {

    static final Duration POLL_INTERVAL = Duration.ofSeconds(15);
    static final Duration APPROVAL_TIMEOUT_INTERVAL = Duration.ofHours(4);
    static final BigDecimal DEFAULT_GAS_BUDGET = new BigDecimal("500");
    static final int MAX_AUTOMATIC_RETRIES = 3;
    static final int MAX_APPROVAL_TIMEOUTS = 3;

    private static final Duration RPC_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration SIGNING_TIMEOUT = Duration.ofSeconds(10);
    private static final int RPC_MAX_ATTEMPTS = 3;
    private static final int SIGNING_MAX_ATTEMPTS = 2;
    private static final Logger log = Workflow.getLogger(TransactionLifecycleWorkflowImpl.class);
    private static final List<EscalationTier> ESCALATION_TIERS = List.of(
            EscalationTier.builder()
                    .level(0).stuckThreshold(Duration.ofMinutes(5))
                    .gasMultiplier(new BigDecimal("1.5")).requiresHumanApproval(false)
                    .description("Wait").build(),
            EscalationTier.builder()
                    .level(1).stuckThreshold(Duration.ofMinutes(15))
                    .gasMultiplier(new BigDecimal("2.0")).requiresHumanApproval(false)
                    .description("Speed-up").build(),
            EscalationTier.builder()
                    .level(2).stuckThreshold(Duration.ofMinutes(30))
                    .gasMultiplier(new BigDecimal("3.0")).requiresHumanApproval(true)
                    .description("Human escalation").build());

    private final TransactionLifecycleActivities rpcActivities;
    private final TransactionLifecycleActivities signingActivities;

    private String transactionId;
    private String intentId;
    private String chain;
    private TransactionStatus currentState;
    private String txHash;
    private int retryCount;
    private BigDecimal totalGasSpent = BigDecimal.ZERO;
    private BigDecimal gasBudget = BigDecimal.ZERO;
    private String gasDenomination;
    private EscalationTier currentTier;
    private SubmissionResource currentResource;
    private HumanApproval pendingApproval;
    private boolean cancelRequested;
    private CancelRequest cancelRequest;

    public TransactionLifecycleWorkflowImpl() {
        this.rpcActivities = Workflow.newActivityStub(
                TransactionLifecycleActivities.class, rpcActivityOptions());
        this.signingActivities = Workflow.newActivityStub(
                TransactionLifecycleActivities.class, signingActivityOptions());
    }

    @Override
    public TransactionResult process(TransactionIntent intent) {
        Workflow.getVersion("STR-28-initial", Workflow.DEFAULT_VERSION, 1);

        transactionId = Workflow.randomUUID().toString();
        intentId = intent.intentId();
        chain = intent.chain();
        gasBudget = DEFAULT_GAS_BUDGET;

        log.info("Starting transaction lifecycle for intent {}", intentId);
        transition(RECEIVED);
        publishStatusEvent(RECEIVED, null);

        try {
            currentResource = rpcActivities.acquireResource(intent);
            transition(BUILDING);

            var unsigned = rpcActivities.build(intent, currentResource);
            gasDenomination = unsigned.feeEstimate().denomination();
            transition(SIGNING);

            var signed = signingActivities.sign(unsigned, currentResource.fromAddress());
            var broadcastResult = rpcActivities.broadcast(signed, chain);
            txHash = broadcastResult.txHash();
            transition(PENDING);
            publishStatusEvent(SUBMITTED, null);

            monitorAndRecover(intent);
        } catch (Exception e) {
            log.error("Transaction {} failed mid-flight: {}", transactionId, e.getMessage());
            releaseCurrentResource();
            if (!currentState.isTerminal()) {
                transition(FAILED);
                publishStatusEvent(FAILED, Map.of("reason", "activity_failure"));
            }
        }

        log.info("Transaction lifecycle completed for intent {} with status {}", intentId, currentState);

        return TransactionResult.builder()
                .transactionId(transactionId)
                .intentId(intentId)
                .finalStatus(currentState)
                .txHash(txHash)
                .totalGasSpent(totalGasSpent)
                .gasDenomination(gasDenomination)
                .totalAttempts(retryCount)
                .completedAt(workflowNow())
                .build();
    }

    @Override
    public void approveRecovery(HumanApproval approval) {
        this.pendingApproval = approval;
    }

    @Override
    public void cancelTransaction(CancelRequest request) {
        this.cancelRequested = true;
        this.cancelRequest = request;
    }

    @Override
    public TransactionSnapshot getStatus() {
        return TransactionSnapshot.builder()
                .transactionId(transactionId)
                .intentId(intentId)
                .status(currentState)
                .txHash(txHash)
                .retryCount(retryCount)
                .gasSpent(totalGasSpent)
                .currentTier(currentTier)
                .updatedAt(workflowNow())
                .build();
    }

    private void monitorAndRecover(TransactionIntent intent) {
        while (!currentState.isTerminal()) {
            if (cancelRequested) {
                handleCancellation();
                return;
            }

            var polledStatus = rpcActivities.checkStatus(txHash, chain);

            switch (polledStatus) {
                case CONFIRMED -> handleConfirmed();
                case STUCK -> handleStuck(intent);
                case DROPPED -> handleDropped(intent);
                default -> Workflow.sleep(POLL_INTERVAL);
            }
        }
    }

    private void handleConfirmed() {
        transition(CONFIRMED);
        var confirmation = rpcActivities.waitForFinality(txHash, chain);
        if (confirmation.finalized()) {
            rpcActivities.consumeResource(currentResource);
            transition(FINALIZED);
            publishStatusEvent(FINALIZED, null);
        } else {
            log.info("Transaction {} confirmed but not yet finalized ({}/{}), resuming monitoring",
                    transactionId, confirmation.confirmations(), confirmation.requiredConfirmations());
            transition(PENDING);
        }
    }

    private void handleStuck(TransactionIntent intent) {
        currentTier = ESCALATION_TIERS.get(Math.min(retryCount, ESCALATION_TIERS.size() - 1));
        transition(STUCK);
        var submitted = buildSubmittedTransaction();
        var assessment = rpcActivities.assessStuck(submitted);

        var budgetExhausted = gasBudget.compareTo(BigDecimal.ZERO) > 0
                && totalGasSpent.compareTo(gasBudget) >= 0;
        var tierRequiresHuman = currentTier != null && currentTier.requiresHumanApproval();
        var retryLimitReached = retryCount >= MAX_AUTOMATIC_RETRIES;

        if (budgetExhausted || tierRequiresHuman || retryLimitReached) {
            awaitHumanDecision(intent);
            return;
        }

        if (assessment.recommendedPlan() instanceof RecoveryPlan.Wait wait) {
            log.info("Assessment recommends waiting: {}", wait.reason());
            transition(PENDING);
            Workflow.sleep(wait.estimatedClearance() != null
                    ? wait.estimatedClearance() : POLL_INTERVAL);
            return;
        }

        executeRecoveryPlan(assessment.recommendedPlan());
    }

    private void awaitHumanDecision(TransactionIntent intent) {
        var version = Workflow.getVersion("STR-31-approval-timeout", Workflow.DEFAULT_VERSION, 1);

        transition(AWAITING_HUMAN);
        publishStatusEvent(AWAITING_HUMAN, Map.of(
                "event_type", "transaction.awaiting_human",
                "current_tier", currentTier != null ? String.valueOf(currentTier.level()) : "unknown",
                "retry_count", String.valueOf(retryCount),
                "gas_spent", totalGasSpent.toPlainString(),
                "gas_budget", gasBudget.toPlainString()));
        log.info("Transaction {} requires human approval", transactionId);

        if (version == Workflow.DEFAULT_VERSION) {
            Workflow.await(() -> pendingApproval != null || cancelRequested);
        } else {
            var timeoutCount = 0;
            while (pendingApproval == null && !cancelRequested && timeoutCount < MAX_APPROVAL_TIMEOUTS) {
                var signalled = Workflow.await(APPROVAL_TIMEOUT_INTERVAL,
                        () -> pendingApproval != null || cancelRequested);
                if (!signalled) {
                    timeoutCount++;
                    publishStatusEvent(AWAITING_HUMAN, Map.of(
                            "event_type", "transaction.human.timeout",
                            "timeout_count", String.valueOf(timeoutCount),
                            "max_timeouts", String.valueOf(MAX_APPROVAL_TIMEOUTS)));
                }
            }

            if (pendingApproval == null && !cancelRequested) {
                log.info("Transaction {} auto-aborted after {} approval timeouts", transactionId, MAX_APPROVAL_TIMEOUTS);
                var systemApproval = HumanApproval.builder()
                        .action(ApprovalAction.ABORT)
                        .approvedBy("system")
                        .reason("HUMAN_RESPONSE_TIMEOUT")
                        .approvedAt(workflowNow())
                        .build();
                rpcActivities.recordApproval(transactionId, systemApproval);
                releaseCurrentResource();
                transition(FAILED);
                publishStatusEvent(FAILED, Map.of("reason", "HUMAN_RESPONSE_TIMEOUT"));
                return;
            }
        }

        if (cancelRequested) {
            handleCancellation();
            return;
        }

        var approval = pendingApproval;
        pendingApproval = null;
        rpcActivities.recordApproval(transactionId, approval);

        switch (approval.action()) {
            case RETRY -> {
                log.info("Human approved retry for {}", transactionId);
                gasBudget = gasBudget.add(DEFAULT_GAS_BUDGET);
                retryCount = 0;
                transition(PENDING);
            }
            case CANCEL -> {
                log.info("Human requested cancel for {}", transactionId);
                var cancelPlan = RecoveryPlan.Cancel.builder()
                        .originalTxHash(txHash).build();
                rpcActivities.executeRecovery(cancelPlan, chain);
                releaseCurrentResource();
                transition(CANCELLED);
                publishStatusEvent(CANCELLED, null);
            }
            case ABORT -> {
                log.info("Human aborted {}", transactionId);
                releaseCurrentResource();
                transition(FAILED);
                publishStatusEvent(FAILED, Map.of("reason", "human_abort"));
            }
        }
    }

    private void executeRecoveryPlan(RecoveryPlan plan) {
        transition(RECOVERING);
        var result = rpcActivities.executeRecovery(plan, chain);
        retryCount++;

        if (result == null) {
            transition(PENDING);
            return;
        }

        if (result.gasCost() != null) {
            totalGasSpent = totalGasSpent.add(result.gasCost());
        }

        if (result.outcome() == RecoveryOutcome.REPLACEMENT_SUBMITTED
                && result.replacementTxHash() != null) {
            txHash = result.replacementTxHash();
            log.info("Recovery submitted replacement tx {}", txHash);
        } else if (result.outcome() == RecoveryOutcome.FAILED) {
            log.warn("Recovery attempt failed: {}", result.details());
        }

        transition(PENDING);
    }

    private void handleDropped(TransactionIntent intent) {
        if (retryCount >= MAX_AUTOMATIC_RETRIES) {
            awaitHumanDecision(intent);
            return;
        }
        transition(DROPPED);
        log.info("Transaction {} dropped, resubmitting", transactionId);

        releaseCurrentResource();
        currentResource = rpcActivities.acquireResource(intent);
        var unsigned = rpcActivities.build(intent, currentResource);
        var signed = signingActivities.sign(unsigned, currentResource.fromAddress());
        var broadcastResult = rpcActivities.broadcast(signed, chain);
        txHash = broadcastResult.txHash();
        retryCount++;
        transition(PENDING);
    }

    private void handleCancellation() {
        log.info("Processing cancellation for {}", transactionId);
        if (txHash != null) {
            var cancelPlan = RecoveryPlan.Cancel.builder()
                    .originalTxHash(txHash).build();
            rpcActivities.executeRecovery(cancelPlan, chain);
        }
        releaseCurrentResource();
        transition(CANCELLED);
        publishStatusEvent(CANCELLED, cancelRequest != null
                ? Map.of("requestedBy", cancelRequest.requestedBy()) : Map.of());
    }

    private void releaseCurrentResource() {
        if (currentResource != null) {
            rpcActivities.releaseResource(currentResource);
        }
    }

    private SubmittedTransaction buildSubmittedTransaction() {
        return SubmittedTransaction.builder()
                .transactionId(transactionId)
                .intentId(intentId)
                .chain(chain)
                .txHash(txHash)
                .fromAddress(currentResource != null ? currentResource.fromAddress() : null)
                .resource(currentResource)
                .status(currentState)
                .retryCount(retryCount)
                .gasSpent(totalGasSpent)
                .gasBudget(gasBudget)
                .currentTier(currentTier)
                .submittedAt(workflowNow())
                .build();
    }

    private void transition(TransactionStatus newState) {
        currentState = newState;
    }

    private void publishStatusEvent(TransactionStatus status, Map<String, String> metadata) {
        var event = TransactionLifecycleEvent.builder()
                .eventId(Workflow.randomUUID().toString())
                .intentId(intentId)
                .transactionHash(txHash)
                .chain(chain)
                .status(status)
                .timestamp(workflowNow())
                .metadata(metadata != null ? metadata : Map.of())
                .build();
        rpcActivities.publishEvent(event);
    }

    private Instant workflowNow() {
        return Instant.ofEpochMilli(Workflow.currentTimeMillis());
    }

    private static ActivityOptions rpcActivityOptions() {
        return ActivityOptions.newBuilder()
                .setStartToCloseTimeout(RPC_TIMEOUT)
                .setRetryOptions(RetryOptions.newBuilder()
                        .setMaximumAttempts(RPC_MAX_ATTEMPTS)
                        .setInitialInterval(Duration.ofSeconds(1))
                        .setBackoffCoefficient(2.0)
                        .setDoNotRetry(
                                "com.stablebridge.txrecovery.domain.exception.NonRetryableException",
                                "com.stablebridge.txrecovery.domain.exception.NonceTooLowException")
                        .build())
                .build();
    }

    private static ActivityOptions signingActivityOptions() {
        return ActivityOptions.newBuilder()
                .setStartToCloseTimeout(SIGNING_TIMEOUT)
                .setRetryOptions(RetryOptions.newBuilder()
                        .setMaximumAttempts(SIGNING_MAX_ATTEMPTS)
                        .setInitialInterval(Duration.ofSeconds(1))
                        .setBackoffCoefficient(2.0)
                        .setDoNotRetry(
                                "com.stablebridge.txrecovery.domain.exception.NonRetryableException",
                                "com.stablebridge.txrecovery.domain.exception.NonceTooLowException")
                        .build())
                .build();
    }
}
