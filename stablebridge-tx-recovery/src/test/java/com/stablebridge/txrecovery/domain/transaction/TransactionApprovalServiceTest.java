package com.stablebridge.txrecovery.domain.transaction;

import static com.stablebridge.txrecovery.testutil.TestUtils.eqIgnoring;
import static com.stablebridge.txrecovery.testutil.TestUtils.eqIgnoringTimestamps;
import static com.stablebridge.txrecovery.testutil.fixtures.ApprovalControllerFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import com.stablebridge.txrecovery.domain.recovery.model.CancelRequest;
import com.stablebridge.txrecovery.domain.recovery.model.HumanApproval;
import com.stablebridge.txrecovery.domain.transaction.model.ApprovalResult;
import com.stablebridge.txrecovery.domain.transaction.model.CancellationResult;
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
            // given
            given(transactionProjectionStore.findById(SOME_APPROVAL_TRANSACTION_ID))
                    .willReturn(Optional.of(AWAITING_HUMAN_PROJECTION));

            // when
            var result = transactionApprovalService.approveTransaction(
                    SOME_APPROVAL_TRANSACTION_ID, ApprovalAction.RETRY, SOME_APPROVAL_REASON, "system");

            // then
            var expected = ApprovalResult.builder()
                    .transactionId(SOME_APPROVAL_TRANSACTION_ID)
                    .status(TransactionStatus.AWAITING_HUMAN)
                    .action(ApprovalAction.RETRY)
                    .build();
            assertThat(result)
                    .usingRecursiveComparison()
                    .ignoringFields("approvedAt")
                    .isEqualTo(expected);
            assertThat(result.approvedAt()).isNotNull();

            var expectedApproval = HumanApproval.builder()
                    .action(ApprovalAction.RETRY)
                    .approvedBy("system")
                    .reason(SOME_APPROVAL_REASON)
                    .build();
            then(transactionWorkflowSignaler).should()
                    .signalApproveRecovery(
                            eqIgnoring(SOME_APPROVAL_TRANSACTION_ID),
                            eqIgnoringTimestamps(expectedApproval));
        }

        @Test
        void shouldApproveWithAbortAction() {
            // given
            given(transactionProjectionStore.findById(SOME_APPROVAL_TRANSACTION_ID))
                    .willReturn(Optional.of(AWAITING_HUMAN_PROJECTION));

            // when
            var result = transactionApprovalService.approveTransaction(
                    SOME_APPROVAL_TRANSACTION_ID, ApprovalAction.ABORT, SOME_APPROVAL_REASON, "system");

            // then
            var expected = ApprovalResult.builder()
                    .transactionId(SOME_APPROVAL_TRANSACTION_ID)
                    .status(TransactionStatus.AWAITING_HUMAN)
                    .action(ApprovalAction.ABORT)
                    .build();
            assertThat(result)
                    .usingRecursiveComparison()
                    .ignoringFields("approvedAt")
                    .isEqualTo(expected);
        }

        @Test
        void shouldThrowWhenTransactionNotFound() {
            // given
            given(transactionProjectionStore.findById("non-existent-tx"))
                    .willReturn(Optional.empty());

            // when/then
            assertThatThrownBy(() -> transactionApprovalService.approveTransaction(
                    "non-existent-tx", ApprovalAction.RETRY, SOME_APPROVAL_REASON, "system"))
                    .isInstanceOf(TransactionNotFoundException.class)
                    .hasMessageContaining("non-existent-tx");
        }

        @Test
        void shouldThrowWhenTransactionNotInAwaitingHumanStatus() {
            // given
            given(transactionProjectionStore.findById(SOME_APPROVAL_TRANSACTION_ID))
                    .willReturn(Optional.of(PENDING_PROJECTION));

            // when/then
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
            // given
            given(transactionProjectionStore.findById(SOME_APPROVAL_TRANSACTION_ID))
                    .willReturn(Optional.of(PENDING_PROJECTION));

            // when
            var result = transactionApprovalService.cancelTransaction(
                    SOME_APPROVAL_TRANSACTION_ID, SOME_CANCEL_REASON, "system");

            // then
            var expected = CancellationResult.builder()
                    .transactionId(SOME_APPROVAL_TRANSACTION_ID)
                    .status(TransactionStatus.CANCELLING)
                    .message("Cancellation requested for transaction %s".formatted(SOME_APPROVAL_TRANSACTION_ID))
                    .build();
            assertThat(result)
                    .usingRecursiveComparison()
                    .isEqualTo(expected);

            var expectedRequest = CancelRequest.builder()
                    .requestedBy("system")
                    .reason(SOME_CANCEL_REASON)
                    .build();
            then(transactionWorkflowSignaler).should()
                    .signalCancelTransaction(
                            eqIgnoring(SOME_APPROVAL_TRANSACTION_ID),
                            eqIgnoringTimestamps(expectedRequest));
        }

        @Test
        void shouldCancelTransactionInStuckStatus() {
            // given
            given(transactionProjectionStore.findById(SOME_APPROVAL_TRANSACTION_ID))
                    .willReturn(Optional.of(STUCK_PROJECTION));

            // when
            var result = transactionApprovalService.cancelTransaction(
                    SOME_APPROVAL_TRANSACTION_ID, SOME_CANCEL_REASON, "system");

            // then
            var expected = CancellationResult.builder()
                    .transactionId(SOME_APPROVAL_TRANSACTION_ID)
                    .status(TransactionStatus.CANCELLING)
                    .message("Cancellation requested for transaction %s".formatted(SOME_APPROVAL_TRANSACTION_ID))
                    .build();
            assertThat(result)
                    .usingRecursiveComparison()
                    .isEqualTo(expected);
        }

        @Test
        void shouldCancelTransactionInAwaitingHumanStatus() {
            // given
            given(transactionProjectionStore.findById(SOME_APPROVAL_TRANSACTION_ID))
                    .willReturn(Optional.of(AWAITING_HUMAN_PROJECTION));

            // when
            var result = transactionApprovalService.cancelTransaction(
                    SOME_APPROVAL_TRANSACTION_ID, SOME_CANCEL_REASON, "system");

            // then
            var expected = CancellationResult.builder()
                    .transactionId(SOME_APPROVAL_TRANSACTION_ID)
                    .status(TransactionStatus.CANCELLING)
                    .message("Cancellation requested for transaction %s".formatted(SOME_APPROVAL_TRANSACTION_ID))
                    .build();
            assertThat(result)
                    .usingRecursiveComparison()
                    .isEqualTo(expected);
        }

        @Test
        void shouldThrowWhenTransactionNotFound() {
            // given
            given(transactionProjectionStore.findById("non-existent-tx"))
                    .willReturn(Optional.empty());

            // when/then
            assertThatThrownBy(() -> transactionApprovalService.cancelTransaction(
                    "non-existent-tx", SOME_CANCEL_REASON, "system"))
                    .isInstanceOf(TransactionNotFoundException.class)
                    .hasMessageContaining("non-existent-tx");
        }

        @Test
        void shouldThrowWhenTransactionIsFinalized() {
            // given
            given(transactionProjectionStore.findById(SOME_APPROVAL_TRANSACTION_ID))
                    .willReturn(Optional.of(FINALIZED_PROJECTION));

            // when/then
            assertThatThrownBy(() -> transactionApprovalService.cancelTransaction(
                    SOME_APPROVAL_TRANSACTION_ID, SOME_CANCEL_REASON, "system"))
                    .isInstanceOf(TerminalTransactionException.class)
                    .hasMessageContaining("FINALIZED");
        }

        @Test
        void shouldThrowWhenTransactionIsFailed() {
            // given
            given(transactionProjectionStore.findById(SOME_APPROVAL_TRANSACTION_ID))
                    .willReturn(Optional.of(FAILED_PROJECTION));

            // when/then
            assertThatThrownBy(() -> transactionApprovalService.cancelTransaction(
                    SOME_APPROVAL_TRANSACTION_ID, SOME_CANCEL_REASON, "system"))
                    .isInstanceOf(TerminalTransactionException.class)
                    .hasMessageContaining("FAILED");
        }

        @Test
        void shouldThrowWhenTransactionIsCancelled() {
            // given
            given(transactionProjectionStore.findById(SOME_APPROVAL_TRANSACTION_ID))
                    .willReturn(Optional.of(CANCELLED_PROJECTION));

            // when/then
            assertThatThrownBy(() -> transactionApprovalService.cancelTransaction(
                    SOME_APPROVAL_TRANSACTION_ID, SOME_CANCEL_REASON, "system"))
                    .isInstanceOf(TerminalTransactionException.class)
                    .hasMessageContaining("CANCELLED");
        }
    }
}
