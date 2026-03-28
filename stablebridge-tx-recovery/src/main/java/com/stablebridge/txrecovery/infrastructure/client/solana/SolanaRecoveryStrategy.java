package com.stablebridge.txrecovery.infrastructure.client.solana;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.stablebridge.txrecovery.domain.address.model.ChainFamily;
import com.stablebridge.txrecovery.domain.recovery.model.RecoveryOutcome;
import com.stablebridge.txrecovery.domain.recovery.model.RecoveryPlan;
import com.stablebridge.txrecovery.domain.recovery.model.RecoveryResult;
import com.stablebridge.txrecovery.domain.recovery.model.StuckAssessment;
import com.stablebridge.txrecovery.domain.recovery.model.StuckReason;
import com.stablebridge.txrecovery.domain.recovery.model.StuckSeverity;
import com.stablebridge.txrecovery.domain.recovery.port.FeeOracle;
import com.stablebridge.txrecovery.domain.recovery.port.RecoveryStrategy;
import com.stablebridge.txrecovery.domain.transaction.model.SolanaSubmissionResource;
import com.stablebridge.txrecovery.domain.transaction.model.SubmittedTransaction;
import com.stablebridge.txrecovery.domain.transaction.model.TransactionIntent;
import com.stablebridge.txrecovery.domain.transaction.port.SubmissionResourceManager;
import com.stablebridge.txrecovery.domain.transaction.port.TransactionSigner;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
class SolanaRecoveryStrategy implements RecoveryStrategy {

    private static final Duration DEFAULT_WAIT_DURATION = Duration.ofMinutes(5);

    private final SolanaRpcClient rpcClient;
    private final FeeOracle feeOracle;
    private final SolanaTransactionBuilder transactionBuilder;
    private final SubmissionResourceManager submissionResourceManager;

    private final Map<String, SubmittedTransaction> assessedTransactions = new ConcurrentHashMap<>();

    @Override
    public boolean appliesTo(ChainFamily chainFamily) {
        return chainFamily == ChainFamily.SOLANA;
    }

    @Override
    public StuckAssessment assess(SubmittedTransaction transaction) {
        assessedTransactions.put(transaction.txHash(), transaction);

        var statuses = rpcClient.getSignatureStatuses(List.of(transaction.txHash()));
        var status = statuses.isEmpty() ? null : statuses.getFirst();

        if (status != null && status.isConfirmedOrFinalized()) {
            return assessConfirmedTransaction();
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
            case RecoveryPlan.Wait wait -> RecoveryResult.builder()
                    .outcome(RecoveryOutcome.WAITING)
                    .details("Waiting estimated %s: %s".formatted(wait.estimatedClearance(), wait.reason()))
                    .build();
            case RecoveryPlan.SpeedUp _ -> throw new IllegalStateException(
                    "Solana does not support SpeedUp — use Resubmit instead");
            case RecoveryPlan.Cancel _ -> throw new IllegalStateException(
                    "Solana does not support Cancel — transactions expire naturally");
        };
    }

    private StuckAssessment assessConfirmedTransaction() {
        return StuckAssessment.builder()
                .reason(StuckReason.NOT_SEEN)
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

        var blockhashValid = rpcClient.isBlockhashValid(solanaResource.nonceValue(), SolanaCommitment.CONFIRMED);

        if (!blockhashValid) {
            return StuckAssessment.builder()
                    .reason(StuckReason.EXPIRED)
                    .severity(StuckSeverity.HIGH)
                    .recommendedPlan(RecoveryPlan.Resubmit.builder()
                            .originalTxHash(transaction.txHash())
                            .build())
                    .explanation("Blockhash expired for nonce value: " + solanaResource.nonceValue())
                    .build();
        }

        return StuckAssessment.builder()
                .reason(StuckReason.NOT_PROPAGATED)
                .severity(StuckSeverity.LOW)
                .recommendedPlan(RecoveryPlan.Wait.builder()
                        .estimatedClearance(DEFAULT_WAIT_DURATION)
                        .reason("Transaction not yet propagated, blockhash still valid")
                        .build())
                .explanation("Transaction not found but blockhash still valid — waiting for propagation")
                .build();
    }

    private RecoveryResult executeResubmit(RecoveryPlan.Resubmit resubmit, TransactionSigner signer) {
        var transaction = assessedTransactions.get(resubmit.originalTxHash());
        if (transaction == null) {
            throw new IllegalStateException(
                    "Transaction not previously assessed: " + resubmit.originalTxHash());
        }
        var originalResource = (SolanaSubmissionResource) transaction.resource();

        var intent = TransactionIntent.builder()
                .intentId("recovery-resubmit-" + resubmit.originalTxHash())
                .chain(originalResource.chain())
                .toAddress(originalResource.fromAddress())
                .amount(BigDecimal.ZERO)
                .token("USDC")
                .build();

        var freshResource = (SolanaSubmissionResource) submissionResourceManager.acquire(intent);
        var unsignedTx = transactionBuilder.build(intent, freshResource);
        var signedTx = signer.sign(unsignedTx, originalResource.fromAddress());
        var txHash = rpcClient.sendTransaction(signedTx.signedPayload());

        submissionResourceManager.release(originalResource);

        return RecoveryResult.builder()
                .outcome(RecoveryOutcome.REPLACEMENT_SUBMITTED)
                .replacementTxHash(txHash)
                .details("Resubmit with fresh nonce: nonceAccount=%s"
                        .formatted(freshResource.nonceAccountAddress()))
                .build();
    }
}
