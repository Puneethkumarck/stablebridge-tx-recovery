package com.stablebridge.txrecovery.infrastructure.client.solana;

import java.time.Duration;
import java.util.List;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.stablebridge.txrecovery.domain.address.model.ChainFamily;
import com.stablebridge.txrecovery.domain.exception.UnsupportedRecoveryOperationException;
import com.stablebridge.txrecovery.domain.recovery.model.RecoveryOutcome;
import com.stablebridge.txrecovery.domain.recovery.model.RecoveryPlan;
import com.stablebridge.txrecovery.domain.recovery.model.RecoveryResult;
import com.stablebridge.txrecovery.domain.recovery.model.StuckAssessment;
import com.stablebridge.txrecovery.domain.recovery.model.StuckReason;
import com.stablebridge.txrecovery.domain.recovery.model.StuckSeverity;
import com.stablebridge.txrecovery.domain.recovery.port.RecoveryStrategy;
import com.stablebridge.txrecovery.domain.transaction.model.SolanaSubmissionResource;
import com.stablebridge.txrecovery.domain.transaction.model.SubmittedTransaction;
import com.stablebridge.txrecovery.domain.transaction.model.TransactionIntent;
import com.stablebridge.txrecovery.domain.transaction.port.SubmissionResourceManager;
import com.stablebridge.txrecovery.domain.transaction.port.TransactionIntentStore;
import com.stablebridge.txrecovery.domain.transaction.port.TransactionSigner;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
class SolanaRecoveryStrategy implements RecoveryStrategy {

    private static final Duration DEFAULT_WAIT_DURATION = Duration.ofMinutes(5);
    private static final int MAX_ASSESSED_TRANSACTIONS = 10_000;

    private final SolanaRpcClient rpcClient;
    private final SolanaTransactionBuilder transactionBuilder;
    private final SubmissionResourceManager submissionResourceManager;
    private final TransactionIntentStore transactionIntentStore;

    private final Cache<String, SubmittedTransaction> assessedTransactions = Caffeine.newBuilder()
            .maximumSize(MAX_ASSESSED_TRANSACTIONS)
            .build();

    @Override
    public boolean appliesTo(ChainFamily chainFamily) {
        return chainFamily == ChainFamily.SOLANA;
    }

    @Override
    public StuckAssessment assess(SubmittedTransaction transaction) {
        assessedTransactions.put(transaction.txHash(), transaction);

        var statuses = rpcClient.getSignatureStatuses(List.of(transaction.txHash()));
        var status = (statuses == null || statuses.isEmpty()) ? null : statuses.getFirst();

        if (status != null && status.isConfirmedOrFinalized()) {
            return assessConfirmingTransaction();
        }

        if (status != null && status.hasError()) {
            return assessErrorTransaction(transaction);
        }

        return assessNotFoundTransaction(transaction);
    }

    @Override
    public RecoveryResult execute(RecoveryPlan plan, TransactionSigner signer) {
        return switch (plan) {
            case RecoveryPlan.Resubmit resubmit -> executeResubmit(resubmit, signer);
            case RecoveryPlan.SpeedUp speedUp -> executeSpeedUp(speedUp, signer);
            case RecoveryPlan.Wait wait -> RecoveryResult.builder()
                    .outcome(RecoveryOutcome.WAITING)
                    .details("Waiting estimated %s: %s".formatted(wait.estimatedClearance(), wait.reason()))
                    .build();
            case RecoveryPlan.Cancel _ -> RecoveryResult.builder()
                    .outcome(RecoveryOutcome.WAITING)
                    .details("Solana transactions expire naturally when blockhash/nonce becomes invalid")
                    .build();
        };
    }

    private StuckAssessment assessConfirmingTransaction() {
        return StuckAssessment.builder()
                .reason(StuckReason.NOT_PROPAGATED)
                .severity(StuckSeverity.LOW)
                .recommendedPlan(RecoveryPlan.Wait.builder()
                        .estimatedClearance(DEFAULT_WAIT_DURATION)
                        .reason("Transaction is confirming on chain")
                        .build())
                .explanation("Transaction already confirmed or finalizing on chain")
                .build();
    }

    private StuckAssessment assessErrorTransaction(SubmittedTransaction transaction) {
        return StuckAssessment.builder()
                .reason(StuckReason.EXPIRED)
                .severity(StuckSeverity.HIGH)
                .recommendedPlan(RecoveryPlan.Resubmit.builder()
                        .originalTxHash(transaction.txHash())
                        .build())
                .explanation("Transaction failed on chain with error")
                .build();
    }

    private StuckAssessment assessNotFoundTransaction(SubmittedTransaction transaction) {
        if (transaction.resource() instanceof SolanaSubmissionResource solanaResource) {
            return assessWithDurableNonce(transaction, solanaResource);
        }

        return StuckAssessment.builder()
                .reason(StuckReason.NOT_PROPAGATED)
                .severity(StuckSeverity.MEDIUM)
                .recommendedPlan(RecoveryPlan.Resubmit.builder()
                        .originalTxHash(transaction.txHash())
                        .build())
                .explanation("Transaction not found, no durable nonce info — resubmit recommended")
                .build();
    }

    private StuckAssessment assessWithDurableNonce(
            SubmittedTransaction transaction, SolanaSubmissionResource solanaResource) {
        var currentNonce = rpcClient.getNonce(solanaResource.nonceAccountAddress(), SolanaCommitment.CONFIRMED);

        if (!currentNonce.equals(solanaResource.nonceValue())) {
            return StuckAssessment.builder()
                    .reason(StuckReason.NONCE_CONSUMED)
                    .severity(StuckSeverity.HIGH)
                    .recommendedPlan(RecoveryPlan.Wait.builder()
                            .estimatedClearance(DEFAULT_WAIT_DURATION)
                            .reason("Nonce already advanced, transaction may have been processed")
                            .build())
                    .explanation("Durable nonce consumed: current=%s, original=%s"
                            .formatted(currentNonce, solanaResource.nonceValue()))
                    .build();
        }

        return StuckAssessment.builder()
                .reason(StuckReason.NOT_SEEN)
                .severity(StuckSeverity.LOW)
                .recommendedPlan(RecoveryPlan.Wait.builder()
                        .estimatedClearance(DEFAULT_WAIT_DURATION)
                        .reason("Transaction not yet seen by cluster, durable nonce still valid")
                        .build())
                .explanation("Transaction not found but durable nonce still valid — waiting for propagation")
                .build();
    }

    private RecoveryResult executeResubmit(RecoveryPlan.Resubmit resubmit, TransactionSigner signer) {
        var transaction = lookupAssessedTransaction(resubmit.originalTxHash());
        assessedTransactions.invalidate(resubmit.originalTxHash());
        var originalResource = requireSolanaResource(transaction);
        var originalIntent = lookupOriginalIntent(transaction.intentId());

        var recoveryIntent = originalIntent.toBuilder()
                .intentId("recovery-resubmit-" + resubmit.originalTxHash())
                .build();

        var freshResource = (SolanaSubmissionResource) submissionResourceManager.acquire(recoveryIntent);
        try {
            var unsignedTx = transactionBuilder.build(recoveryIntent, freshResource);
            var signedTx = signer.sign(unsignedTx, originalResource.fromAddress());
            var txHash = rpcClient.sendTransaction(signedTx.signedPayload());

            submissionResourceManager.release(originalResource);

            return RecoveryResult.builder()
                    .outcome(RecoveryOutcome.REPLACEMENT_SUBMITTED)
                    .replacementTxHash(txHash)
                    .details("Resubmit with fresh nonce: nonceAccount=%s"
                            .formatted(freshResource.nonceAccountAddress()))
                    .build();
        } catch (RuntimeException ex) {
            submissionResourceManager.release(freshResource);
            throw ex;
        }
    }

    private RecoveryResult executeSpeedUp(RecoveryPlan.SpeedUp speedUp, TransactionSigner signer) {
        var transaction = lookupAssessedTransaction(speedUp.originalTxHash());
        var originalResource = requireSolanaResource(transaction);
        var originalIntent = lookupOriginalIntent(transaction.intentId());

        var recoveryIntent = originalIntent.toBuilder()
                .intentId("recovery-speedup-" + speedUp.originalTxHash())
                .build();

        var unsignedTx = transactionBuilder.build(recoveryIntent, originalResource);
        var signedTx = signer.sign(unsignedTx, originalResource.fromAddress());
        var txHash = rpcClient.sendTransaction(signedTx.signedPayload());

        return RecoveryResult.builder()
                .outcome(RecoveryOutcome.REPLACEMENT_SUBMITTED)
                .replacementTxHash(txHash)
                .gasCost(speedUp.newFee().estimatedCost())
                .details("Speed-up with same nonce: nonceAccount=%s"
                        .formatted(originalResource.nonceAccountAddress()))
                .build();
    }

    private SubmittedTransaction lookupAssessedTransaction(String txHash) {
        var transaction = assessedTransactions.getIfPresent(txHash);
        if (transaction == null) {
            throw new UnsupportedRecoveryOperationException(
                    "Transaction not previously assessed: " + txHash);
        }
        return transaction;
    }

    private SolanaSubmissionResource requireSolanaResource(SubmittedTransaction transaction) {
        if (transaction.resource() instanceof SolanaSubmissionResource solanaResource) {
            return solanaResource;
        }
        throw new UnsupportedRecoveryOperationException(
                "Expected SolanaSubmissionResource for transaction: " + transaction.txHash());
    }

    private TransactionIntent lookupOriginalIntent(String intentId) {
        return transactionIntentStore.findByIntentId(intentId)
                .orElseThrow(() -> new UnsupportedRecoveryOperationException(
                        "Original transaction intent not found: " + intentId));
    }
}
