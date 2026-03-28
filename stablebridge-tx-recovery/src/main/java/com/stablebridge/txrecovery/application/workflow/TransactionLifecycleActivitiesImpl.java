package com.stablebridge.txrecovery.application.workflow;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.stablebridge.txrecovery.domain.address.model.ChainFamily;
import com.stablebridge.txrecovery.domain.exception.NonRetryableException;
import com.stablebridge.txrecovery.domain.recovery.model.EscalationPolicy;
import com.stablebridge.txrecovery.domain.recovery.model.EscalationTier;
import com.stablebridge.txrecovery.domain.recovery.model.GasBudgetPolicy;
import com.stablebridge.txrecovery.domain.recovery.model.RecoveryPlan;
import com.stablebridge.txrecovery.domain.recovery.model.RecoveryResult;
import com.stablebridge.txrecovery.domain.recovery.model.StuckAssessment;
import com.stablebridge.txrecovery.domain.recovery.port.RecoveryStrategy;
import com.stablebridge.txrecovery.domain.transaction.event.TransactionLifecycleEvent;
import com.stablebridge.txrecovery.domain.transaction.model.BroadcastResult;
import com.stablebridge.txrecovery.domain.transaction.model.ConfirmationStatus;
import com.stablebridge.txrecovery.domain.transaction.model.EvmSubmissionResource;
import com.stablebridge.txrecovery.domain.transaction.model.SignedTransaction;
import com.stablebridge.txrecovery.domain.transaction.model.SolanaSubmissionResource;
import com.stablebridge.txrecovery.domain.transaction.model.SubmissionResource;
import com.stablebridge.txrecovery.domain.transaction.model.SubmittedTransaction;
import com.stablebridge.txrecovery.domain.transaction.model.TransactionIntent;
import com.stablebridge.txrecovery.domain.transaction.model.TransactionStatus;
import com.stablebridge.txrecovery.domain.transaction.model.UnsignedTransaction;
import com.stablebridge.txrecovery.domain.transaction.port.ChainTransactionManager;
import com.stablebridge.txrecovery.domain.transaction.port.SubmissionResourceManager;
import com.stablebridge.txrecovery.domain.transaction.port.TransactionEventPublisher;
import com.stablebridge.txrecovery.domain.transaction.port.TransactionSigner;

import io.temporal.activity.Activity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class TransactionLifecycleActivitiesImpl implements TransactionLifecycleActivities {

    private final Map<ChainFamily, ChainTransactionManager> chainTransactionManagers;
    private final Map<ChainFamily, SubmissionResourceManager> submissionResourceManagers;
    private final List<RecoveryStrategy> recoveryStrategies;
    private final TransactionSigner transactionSigner;
    private final TransactionEventPublisher eventPublisher;
    private final GasBudgetPolicy gasBudgetPolicy;
    private final EscalationPolicy escalationPolicy;
    private final Map<String, ChainFamily> chainFamilyMapping;
    private final Duration defaultPollInterval;

    @Override
    public SubmissionResource acquireResource(TransactionIntent intent) {
        var family = resolveChainFamily(intent.chain());
        log.info("Acquiring resource for chain={} family={}", intent.chain(), family);
        return findSubmissionResourceManager(family).acquire(intent);
    }

    @Override
    public void releaseResource(SubmissionResource resource) {
        var family = resolveChainFamilyFromResource(resource);
        log.info("Releasing resource for chain={} family={}", resource.chain(), family);
        findSubmissionResourceManager(family).release(resource);
    }

    @Override
    public void consumeResource(SubmissionResource resource) {
        var family = resolveChainFamilyFromResource(resource);
        log.info("Consuming resource for chain={} family={}", resource.chain(), family);
        findSubmissionResourceManager(family).consume(resource);
    }

    @Override
    public UnsignedTransaction build(TransactionIntent intent, SubmissionResource resource) {
        var family = resolveChainFamilyFromResource(resource);
        return findChainTransactionManager(family).build(intent, resource);
    }

    @Override
    public SignedTransaction sign(UnsignedTransaction transaction, String fromAddress) {
        return transactionSigner.sign(transaction, fromAddress);
    }

    @Override
    public BroadcastResult broadcast(SignedTransaction signedTransaction, String chain) {
        var family = resolveChainFamily(chain);
        return findChainTransactionManager(family).broadcast(signedTransaction, chain);
    }

    @Override
    public TransactionStatus checkStatus(String txHash, String chain) {
        var family = resolveChainFamily(chain);
        return findChainTransactionManager(family).checkStatus(txHash, chain);
    }

    @Override
    public ConfirmationStatus waitForFinality(String txHash, String chain) {
        var family = resolveChainFamily(chain);
        var manager = findChainTransactionManager(family);
        var status = manager.getConfirmationStatus(txHash, chain);
        Activity.getExecutionContext().heartbeat(status);
        return status;
    }

    @Override
    public Duration getPollInterval(String chain) {
        return defaultPollInterval;
    }

    @Override
    public BigDecimal calculateGasBudget(BigDecimal txValueUsd) {
        return gasBudgetPolicy.calculateBudget(txValueUsd);
    }

    @Override
    public StuckAssessment assessStuck(SubmittedTransaction transaction) {
        var family = resolveChainFamily(transaction.chain());
        return findRecoveryStrategy(family).assess(transaction);
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
        var family = resolveChainFamily(chain);
        return findRecoveryStrategy(family).execute(plan, transactionSigner);
    }

    @Override
    public RecoveryResult cancelOnChain(String txHash, String chain) {
        var family = resolveChainFamily(chain);
        var cancelPlan = RecoveryPlan.Cancel.builder().originalTxHash(txHash).build();
        return findRecoveryStrategy(family).execute(cancelPlan, transactionSigner);
    }

    @Override
    public void publishEvent(TransactionLifecycleEvent event) {
        eventPublisher.publish(event);
    }

    private ChainFamily resolveChainFamily(String chain) {
        return Optional.ofNullable(chainFamilyMapping.get(chain))
                .orElseThrow(() -> new NonRetryableException(
                        "No chain family mapping found for chain: " + chain));
    }

    private ChainFamily resolveChainFamilyFromResource(SubmissionResource resource) {
        return switch (resource) {
            case EvmSubmissionResource _ -> ChainFamily.EVM;
            case SolanaSubmissionResource _ -> ChainFamily.SOLANA;
        };
    }

    private ChainTransactionManager findChainTransactionManager(ChainFamily family) {
        return Optional.ofNullable(chainTransactionManagers.get(family))
                .orElseThrow(() -> new NonRetryableException(
                        "No ChainTransactionManager registered for family: " + family));
    }

    private SubmissionResourceManager findSubmissionResourceManager(ChainFamily family) {
        return Optional.ofNullable(submissionResourceManagers.get(family))
                .orElseThrow(() -> new NonRetryableException(
                        "No SubmissionResourceManager registered for family: " + family));
    }

    private RecoveryStrategy findRecoveryStrategy(ChainFamily family) {
        return recoveryStrategies.stream()
                .filter(strategy -> strategy.appliesTo(family))
                .findFirst()
                .orElseThrow(() -> new NonRetryableException(
                        "No RecoveryStrategy registered for family: " + family));
    }
}
