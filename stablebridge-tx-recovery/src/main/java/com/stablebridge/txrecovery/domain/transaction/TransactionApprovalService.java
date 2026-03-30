package com.stablebridge.txrecovery.domain.transaction;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stablebridge.txrecovery.domain.exception.InvalidTransactionStateException;
import com.stablebridge.txrecovery.domain.exception.TerminalTransactionException;
import com.stablebridge.txrecovery.domain.exception.TransactionNotFoundException;
import com.stablebridge.txrecovery.domain.recovery.model.ApprovalAction;
import com.stablebridge.txrecovery.domain.recovery.model.CancelRequest;
import com.stablebridge.txrecovery.domain.recovery.model.HumanApproval;
import com.stablebridge.txrecovery.domain.transaction.model.ApprovalResult;
import com.stablebridge.txrecovery.domain.transaction.model.CancellationResult;
import com.stablebridge.txrecovery.domain.transaction.model.TransactionProjection;
import com.stablebridge.txrecovery.domain.transaction.model.TransactionStatus;
import com.stablebridge.txrecovery.domain.transaction.port.TransactionProjectionStore;
import com.stablebridge.txrecovery.domain.transaction.port.TransactionWorkflowSignaler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionApprovalService {

    private final TransactionProjectionStore transactionProjectionStore;
    private final TransactionWorkflowSignaler transactionWorkflowSignaler;

    @Transactional
    public ApprovalResult approveTransaction(
            String transactionId, ApprovalAction action, String reason, String approvedBy) {
        var projection = findProjection(transactionId);
        validateAwaitingHuman(projection);

        var approval = HumanApproval.builder()
                .action(action)
                .approvedBy(approvedBy)
                .reason(reason)
                .approvedAt(Instant.now())
                .build();

        transactionWorkflowSignaler.signalApproveRecovery(transactionId, approval);

        log.info("Approved transaction: transactionId={}, action={}", transactionId, action);

        return ApprovalResult.builder()
                .transactionId(transactionId)
                .status(projection.status())
                .action(action)
                .approvedAt(approval.approvedAt())
                .build();
    }

    @Transactional
    public CancellationResult cancelTransaction(String transactionId, String reason, String requestedBy) {
        var projection = findProjection(transactionId);
        validateNotTerminal(projection);

        var cancelRequest = CancelRequest.builder()
                .requestedBy(requestedBy)
                .reason(reason)
                .requestedAt(Instant.now())
                .build();

        transactionWorkflowSignaler.signalCancelTransaction(transactionId, cancelRequest);

        log.info("Cancel requested for transaction: transactionId={}", transactionId);

        return CancellationResult.builder()
                .transactionId(transactionId)
                .status(TransactionStatus.CANCELLING)
                .message("Cancellation requested for transaction %s".formatted(transactionId))
                .build();
    }

    private TransactionProjection findProjection(String transactionId) {
        return transactionProjectionStore.findById(transactionId)
                .orElseThrow(() -> new TransactionNotFoundException(transactionId));
    }

    private void validateAwaitingHuman(TransactionProjection projection) {
        if (projection.status() != TransactionStatus.AWAITING_HUMAN) {
            throw new InvalidTransactionStateException(
                    projection.transactionId(), projection.status().name());
        }
    }

    private void validateNotTerminal(TransactionProjection projection) {
        if (projection.status().isTerminal()) {
            throw new TerminalTransactionException(
                    projection.transactionId(), projection.status().name());
        }
    }
}
