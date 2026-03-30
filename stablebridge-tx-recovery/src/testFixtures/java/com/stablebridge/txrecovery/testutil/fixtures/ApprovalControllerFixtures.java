package com.stablebridge.txrecovery.testutil.fixtures;

import java.time.Instant;

import com.stablebridge.txrecovery.api.model.ApprovalActionDto;
import com.stablebridge.txrecovery.api.model.ApproveTransactionRequest;
import com.stablebridge.txrecovery.api.model.ApproveTransactionResponse;
import com.stablebridge.txrecovery.api.model.CancelTransactionRequest;
import com.stablebridge.txrecovery.api.model.CancelTransactionResponse;
import com.stablebridge.txrecovery.domain.recovery.model.ApprovalAction;
import com.stablebridge.txrecovery.domain.transaction.model.ApprovalResult;
import com.stablebridge.txrecovery.domain.transaction.model.CancellationResult;
import com.stablebridge.txrecovery.domain.transaction.model.TransactionProjection;
import com.stablebridge.txrecovery.domain.transaction.model.TransactionStatus;

public final class ApprovalControllerFixtures {

    private ApprovalControllerFixtures() {}

    public static final String SOME_APPROVAL_TRANSACTION_ID = "tx-approval-001";
    public static final String SOME_APPROVAL_INTENT_ID = "019576a0-e29b-7000-a716-556655440000";
    public static final String SOME_APPROVAL_CHAIN = "ethereum";
    public static final String SOME_APPROVAL_REASON = "Recovery approved by ops team";
    public static final String SOME_CANCEL_REASON = "Transaction no longer needed";
    public static final Instant SOME_APPROVED_AT = Instant.parse("2026-03-30T10:00:00Z");

    public static final TransactionProjection AWAITING_HUMAN_PROJECTION = TransactionProjection.builder()
            .transactionId(SOME_APPROVAL_TRANSACTION_ID)
            .intentId(SOME_APPROVAL_INTENT_ID)
            .chain(SOME_APPROVAL_CHAIN)
            .status(TransactionStatus.AWAITING_HUMAN)
            .toAddress("0xrecipient0000000000000000000000000000001")
            .retryCount(2)
            .submittedAt(Instant.parse("2026-01-01T00:00:00Z"))
            .build();

    public static final TransactionProjection PENDING_PROJECTION = TransactionProjection.builder()
            .transactionId(SOME_APPROVAL_TRANSACTION_ID)
            .intentId(SOME_APPROVAL_INTENT_ID)
            .chain(SOME_APPROVAL_CHAIN)
            .status(TransactionStatus.PENDING)
            .toAddress("0xrecipient0000000000000000000000000000001")
            .retryCount(1)
            .submittedAt(Instant.parse("2026-01-01T00:00:00Z"))
            .build();

    public static final TransactionProjection STUCK_PROJECTION = TransactionProjection.builder()
            .transactionId(SOME_APPROVAL_TRANSACTION_ID)
            .intentId(SOME_APPROVAL_INTENT_ID)
            .chain(SOME_APPROVAL_CHAIN)
            .status(TransactionStatus.STUCK)
            .toAddress("0xrecipient0000000000000000000000000000001")
            .retryCount(3)
            .submittedAt(Instant.parse("2026-01-01T00:00:00Z"))
            .build();

    public static final TransactionProjection FINALIZED_PROJECTION = TransactionProjection.builder()
            .transactionId(SOME_APPROVAL_TRANSACTION_ID)
            .intentId(SOME_APPROVAL_INTENT_ID)
            .chain(SOME_APPROVAL_CHAIN)
            .status(TransactionStatus.FINALIZED)
            .toAddress("0xrecipient0000000000000000000000000000001")
            .retryCount(0)
            .submittedAt(Instant.parse("2026-01-01T00:00:00Z"))
            .build();

    public static final TransactionProjection FAILED_PROJECTION = TransactionProjection.builder()
            .transactionId(SOME_APPROVAL_TRANSACTION_ID)
            .intentId(SOME_APPROVAL_INTENT_ID)
            .chain(SOME_APPROVAL_CHAIN)
            .status(TransactionStatus.FAILED)
            .toAddress("0xrecipient0000000000000000000000000000001")
            .retryCount(0)
            .submittedAt(Instant.parse("2026-01-01T00:00:00Z"))
            .build();

    public static final TransactionProjection CANCELLED_PROJECTION = TransactionProjection.builder()
            .transactionId(SOME_APPROVAL_TRANSACTION_ID)
            .intentId(SOME_APPROVAL_INTENT_ID)
            .chain(SOME_APPROVAL_CHAIN)
            .status(TransactionStatus.CANCELLED)
            .toAddress("0xrecipient0000000000000000000000000000001")
            .retryCount(0)
            .submittedAt(Instant.parse("2026-01-01T00:00:00Z"))
            .build();

    public static final ApproveTransactionRequest SOME_APPROVE_REQUEST = ApproveTransactionRequest.builder()
            .action(ApprovalActionDto.RETRY)
            .reason(SOME_APPROVAL_REASON)
            .build();

    public static final CancelTransactionRequest SOME_CANCEL_REQUEST = CancelTransactionRequest.builder()
            .reason(SOME_CANCEL_REASON)
            .build();

    public static final ApprovalResult SOME_APPROVAL_RESULT = ApprovalResult.builder()
            .transactionId(SOME_APPROVAL_TRANSACTION_ID)
            .status(TransactionStatus.AWAITING_HUMAN)
            .action(ApprovalAction.RETRY)
            .approvedAt(SOME_APPROVED_AT)
            .build();

    public static final ApproveTransactionResponse SOME_APPROVE_RESPONSE = ApproveTransactionResponse.builder()
            .transactionId(SOME_APPROVAL_TRANSACTION_ID)
            .status("AWAITING_HUMAN")
            .action("RETRY")
            .approvedAt(SOME_APPROVED_AT)
            .build();

    public static final CancellationResult SOME_CANCELLATION_RESULT = CancellationResult.builder()
            .transactionId(SOME_APPROVAL_TRANSACTION_ID)
            .status(TransactionStatus.CANCELLING)
            .message("Cancellation requested for transaction %s".formatted(SOME_APPROVAL_TRANSACTION_ID))
            .build();

    public static final CancelTransactionResponse SOME_CANCEL_RESPONSE = CancelTransactionResponse.builder()
            .transactionId(SOME_APPROVAL_TRANSACTION_ID)
            .status("CANCELLING")
            .message("Cancellation requested for transaction %s".formatted(SOME_APPROVAL_TRANSACTION_ID))
            .build();
}
