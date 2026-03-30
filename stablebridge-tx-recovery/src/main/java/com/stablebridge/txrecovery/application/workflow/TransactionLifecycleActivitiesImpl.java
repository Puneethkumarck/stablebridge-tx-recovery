package com.stablebridge.txrecovery.application.workflow;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Comparator;
import java.util.Map;

import com.stablebridge.txrecovery.application.config.ChainAdapterRegistry;
import com.stablebridge.txrecovery.domain.recovery.model.EscalationPolicy;
import com.stablebridge.txrecovery.domain.recovery.model.EscalationTier;
import com.stablebridge.txrecovery.domain.recovery.model.GasBudgetPolicy;
import com.stablebridge.txrecovery.domain.recovery.model.HumanApproval;
import com.stablebridge.txrecovery.domain.recovery.model.RecoveryPlan;
import com.stablebridge.txrecovery.domain.recovery.model.RecoveryResult;
import com.stablebridge.txrecovery.domain.recovery.model.StuckAssessment;
import com.stablebridge.txrecovery.domain.transaction.event.TransactionLifecycleEvent;
import com.stablebridge.txrecovery.domain.transaction.model.BroadcastResult;
import com.stablebridge.txrecovery.domain.transaction.model.ConfirmationStatus;
import com.stablebridge.txrecovery.domain.transaction.model.SignedTransaction;
import com.stablebridge.txrecovery.domain.transaction.model.SubmissionResource;
import com.stablebridge.txrecovery.domain.transaction.model.SubmittedTransaction;
import com.stablebridge.txrecovery.domain.transaction.model.TransactionIntent;
import com.stablebridge.txrecovery.domain.transaction.model.TransactionStatus;
import com.stablebridge.txrecovery.domain.transaction.model.UnsignedTransaction;
import com.stablebridge.txrecovery.domain.transaction.port.TransactionEventPublisher;
import com.stablebridge.txrecovery.domain.transaction.port.TransactionSigner;

import io.temporal.activity.Activity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class TransactionLifecycleActivitiesImpl implements TransactionLifecycleActivities {

    private final ChainAdapterRegistry chainAdapterRegistry;
    private final TransactionSigner transactionSigner;
    private final TransactionEventPublisher eventPublisher;
    private final GasBudgetPolicy gasBudgetPolicy;
    private final EscalationPolicy escalationPolicy;
    private final Map<String, Duration> chainPollIntervals;

    @Override
    public SubmissionResource acquireResource(TransactionIntent intent) {
        log.info("Acquiring resource for chain={}", intent.chain());
        return chainAdapterRegistry.getResourceManager(intent.chain()).acquire(intent);
    }

    @Override
    public void releaseResource(SubmissionResource resource) {
        log.info("Releasing resource for chain={}", resource.chain());
        chainAdapterRegistry.getResourceManager(resource.chain()).release(resource);
    }

    @Override
    public void consumeResource(SubmissionResource resource) {
        log.info("Consuming resource for chain={}", resource.chain());
        chainAdapterRegistry.getResourceManager(resource.chain()).consume(resource);
    }

    @Override
    public UnsignedTransaction build(TransactionIntent intent, SubmissionResource resource) {
        return chainAdapterRegistry.getTransactionManager(resource.chain()).build(intent, resource);
    }

    @Override
    public SignedTransaction sign(UnsignedTransaction transaction, String fromAddress) {
        return transactionSigner.sign(transaction, fromAddress);
    }

    @Override
    public BroadcastResult broadcast(SignedTransaction signedTransaction, String chain) {
        return chainAdapterRegistry.getTransactionManager(chain).broadcast(signedTransaction, chain);
    }

    @Override
    public TransactionStatus checkStatus(String txHash, String chain) {
        return chainAdapterRegistry.getTransactionManager(chain).checkStatus(txHash, chain);
    }

    @Override
    public ConfirmationStatus waitForFinality(String txHash, String chain) {
        var manager = chainAdapterRegistry.getTransactionManager(chain);
        var status = manager.getConfirmationStatus(txHash, chain);
        Activity.getExecutionContext().heartbeat(status);
        return status;
    }

    @Override
    public Duration getPollInterval(String chain) {
        return chainPollIntervals.getOrDefault(chain, Duration.ofSeconds(12));
    }

    @Override
    public BigDecimal calculateGasBudget(BigDecimal txValueUsd) {
        return gasBudgetPolicy.calculateBudget(txValueUsd);
    }

    @Override
    public StuckAssessment assessStuck(SubmittedTransaction transaction) {
        return chainAdapterRegistry.getRecoveryStrategy(transaction.chain()).assess(transaction);
    }

    @Override
    public EscalationTier determineEscalationTier(Duration stuckDuration) {
        return escalationPolicy.tiers().stream()
                .filter(tier -> stuckDuration.compareTo(tier.stuckThreshold()) >= 0)
                .max(Comparator.comparingInt(EscalationTier::level))
                .orElse(escalationPolicy.tiers().getFirst());
    }

    @Override
    public RecoveryResult executeRecovery(RecoveryPlan plan, String chain) {
        return chainAdapterRegistry.getRecoveryStrategy(chain).execute(plan, transactionSigner);
    }

    @Override
    public RecoveryResult cancelOnChain(String txHash, String chain) {
        var cancelPlan = RecoveryPlan.Cancel.builder().originalTxHash(txHash).build();
        return chainAdapterRegistry.getRecoveryStrategy(chain).execute(cancelPlan, transactionSigner);
    }

    @Override
    public void publishEvent(TransactionLifecycleEvent event) {
        eventPublisher.publish(event);
    }

    @Override
    public void recordApproval(String transactionId, HumanApproval approval) {
        log.info("Recording approval for transaction={} action={} approvedBy={}",
                transactionId, approval.action(), approval.approvedBy());
    }
}
