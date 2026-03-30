package com.stablebridge.txrecovery.domain.transaction;

import static com.stablebridge.txrecovery.testutil.fixtures.ApprovalControllerFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.util.Optional;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.stablebridge.txrecovery.domain.exception.InvalidTransactionStateException;
import com.stablebridge.txrecovery.domain.exception.TerminalTransactionException;
import com.stablebridge.txrecovery.domain.exception.TransactionNotFoundException;
import com.stablebridge.txrecovery.domain.recovery.model.ApprovalAction;
import com.stablebridge.txrecovery.domain.transaction.model.TransactionStatus;
import com.stablebridge.txrecovery.domain.transaction.port.TransactionProjectionStore;
import com.stablebridge.txrecovery.domain.transaction.port.TransactionWorkflowSignaler;

@ExtendWith(MockitoExtension.class)
class TransactionApprovalServiceTest {

    @Mock
    private TransactionProjectionStore transactionProjectionStore;

    @Mock
    private TransactionWorkflowSignaler transactionWorkflowSignaler;

    @InjectMocks
    private TransactionApprovalService transactionApprovalService;

    @Nested
    class ApproveTransaction {

        @Test
        void shouldApproveTransactionInAwaitingHumanStatus() {
            given(transactionProjectionStore.findById(SOME_APPROVAL_TRANSACTION_ID))
                    .willReturn(Optional.of(AWAITING_HUMAN_PROJECTION));

            var result = transactionApprovalService.approveTransaction(
                    SOME_APPROVAL_TRANSACTION_ID, ApprovalAction.RETRY, SOME_APPROVAL_REASON, "system");

            assertThat(result.transactionId()).isEqualTo(SOME_APPROVAL_TRANSACTION_ID);
            assertThat(result.status()).isEqualTo(TransactionStatus.AWAITING_HUMAN);
            assertThat(result.action()).isEqualTo(ApprovalAction.RETRY);
            assertThat(result.approvedAt()).isNotNull();

            then(transactionWorkflowSignaler).should()
                    .signalApproveRecovery(
                            argThat(id -> id.equals(SOME_APPROVAL_TRANSACTION_ID)),
                            argThat(approval -> approval.action() == ApprovalAction.RETRY
                                    && approval.approvedBy().equals("system")
                                    && approval.reason().equals(SOME_APPROVAL_REASON)));
        }

        @Test
        void shouldApproveWithAbortAction() {
            given(transactionProjectionStore.findById(SOME_APPROVAL_TRANSACTION_ID))
                    .willReturn(Optional.of(AWAITING_HUMAN_PROJECTION));

            var result = transactionApprovalService.approveTransaction(
                    SOME_APPROVAL_TRANSACTION_ID, ApprovalAction.ABORT, SOME_APPROVAL_REASON, "system");

            assertThat(result.action()).isEqualTo(ApprovalAction.ABORT);
        }

        @Test
        void shouldThrowWhenTransactionNotFound() {
            given(transactionProjectionStore.findById("non-existent-tx"))
                    .willReturn(Optional.empty());

            assertThatThrownBy(() -> transactionApprovalService.approveTransaction(
                    "non-existent-tx", ApprovalAction.RETRY, SOME_APPROVAL_REASON, "system"))
                    .isInstanceOf(TransactionNotFoundException.class)
                    .hasMessageContaining("non-existent-tx");
        }

        @Test
        void shouldThrowWhenTransactionNotInAwaitingHumanStatus() {
            given(transactionProjectionStore.findById(SOME_APPROVAL_TRANSACTION_ID))
                    .willReturn(Optional.of(PENDING_PROJECTION));

            assertThatThrownBy(() -> transactionApprovalService.approveTransaction(
                    SOME_APPROVAL_TRANSACTION_ID, ApprovalAction.RETRY, SOME_APPROVAL_REASON, "system"))
                    .isInstanceOf(InvalidTransactionStateException.class)
                    .hasMessageContaining("PENDING")
                    .hasMessageContaining("AWAITING_HUMAN");
        }
    }

    @Nested
    class CancelTransaction {

        @Test
        void shouldCancelTransactionInPendingStatus() {
            given(transactionProjectionStore.findById(SOME_APPROVAL_TRANSACTION_ID))
                    .willReturn(Optional.of(PENDING_PROJECTION));

            var result = transactionApprovalService.cancelTransaction(
                    SOME_APPROVAL_TRANSACTION_ID, SOME_CANCEL_REASON, "system");

            assertThat(result.transactionId()).isEqualTo(SOME_APPROVAL_TRANSACTION_ID);
            assertThat(result.status()).isEqualTo(TransactionStatus.CANCELLING);
            assertThat(result.message()).contains(SOME_APPROVAL_TRANSACTION_ID);

            then(transactionWorkflowSignaler).should()
                    .signalCancelTransaction(
                            argThat(id -> id.equals(SOME_APPROVAL_TRANSACTION_ID)),
                            argThat(req -> req.requestedBy().equals("system")
                                    && req.reason().equals(SOME_CANCEL_REASON)));
        }

        @Test
        void shouldCancelTransactionInStuckStatus() {
            given(transactionProjectionStore.findById(SOME_APPROVAL_TRANSACTION_ID))
                    .willReturn(Optional.of(STUCK_PROJECTION));

            var result = transactionApprovalService.cancelTransaction(
                    SOME_APPROVAL_TRANSACTION_ID, SOME_CANCEL_REASON, "system");

            assertThat(result.status()).isEqualTo(TransactionStatus.CANCELLING);
        }

        @Test
        void shouldCancelTransactionInAwaitingHumanStatus() {
            given(transactionProjectionStore.findById(SOME_APPROVAL_TRANSACTION_ID))
                    .willReturn(Optional.of(AWAITING_HUMAN_PROJECTION));

            var result = transactionApprovalService.cancelTransaction(
                    SOME_APPROVAL_TRANSACTION_ID, SOME_CANCEL_REASON, "system");

            assertThat(result.status()).isEqualTo(TransactionStatus.CANCELLING);
        }

        @Test
        void shouldThrowWhenTransactionNotFound() {
            given(transactionProjectionStore.findById("non-existent-tx"))
                    .willReturn(Optional.empty());

            assertThatThrownBy(() -> transactionApprovalService.cancelTransaction(
                    "non-existent-tx", SOME_CANCEL_REASON, "system"))
                    .isInstanceOf(TransactionNotFoundException.class)
                    .hasMessageContaining("non-existent-tx");
        }

        @Test
        void shouldThrowWhenTransactionIsFinalized() {
            given(transactionProjectionStore.findById(SOME_APPROVAL_TRANSACTION_ID))
                    .willReturn(Optional.of(FINALIZED_PROJECTION));

            assertThatThrownBy(() -> transactionApprovalService.cancelTransaction(
                    SOME_APPROVAL_TRANSACTION_ID, SOME_CANCEL_REASON, "system"))
                    .isInstanceOf(TerminalTransactionException.class)
                    .hasMessageContaining("FINALIZED");
        }

        @Test
        void shouldThrowWhenTransactionIsFailed() {
            given(transactionProjectionStore.findById(SOME_APPROVAL_TRANSACTION_ID))
                    .willReturn(Optional.of(FAILED_PROJECTION));

            assertThatThrownBy(() -> transactionApprovalService.cancelTransaction(
                    SOME_APPROVAL_TRANSACTION_ID, SOME_CANCEL_REASON, "system"))
                    .isInstanceOf(TerminalTransactionException.class)
                    .hasMessageContaining("FAILED");
        }

        @Test
        void shouldThrowWhenTransactionIsCancelled() {
            given(transactionProjectionStore.findById(SOME_APPROVAL_TRANSACTION_ID))
                    .willReturn(Optional.of(CANCELLED_PROJECTION));

            assertThatThrownBy(() -> transactionApprovalService.cancelTransaction(
                    SOME_APPROVAL_TRANSACTION_ID, SOME_CANCEL_REASON, "system"))
                    .isInstanceOf(TerminalTransactionException.class)
                    .hasMessageContaining("CANCELLED");
        }
    }
}
