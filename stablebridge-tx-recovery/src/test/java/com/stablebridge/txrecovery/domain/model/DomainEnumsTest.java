package com.stablebridge.txrecovery.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;

import org.junit.jupiter.api.Test;

class DomainEnumsTest {

    @Test
    void shouldHaveThirteenTransactionStatuses() {
        // when
        var values = TransactionStatus.values();

        // then
        assertThat(values).hasSize(13);
    }

    @Test
    void shouldContainExpectedTransactionStatuses() {
        // when / then
        assertThat(TransactionStatus.valueOf("RECEIVED")).isEqualTo(TransactionStatus.RECEIVED);
        assertThat(TransactionStatus.valueOf("BUILDING")).isEqualTo(TransactionStatus.BUILDING);
        assertThat(TransactionStatus.valueOf("SIGNING")).isEqualTo(TransactionStatus.SIGNING);
        assertThat(TransactionStatus.valueOf("SUBMITTED")).isEqualTo(TransactionStatus.SUBMITTED);
        assertThat(TransactionStatus.valueOf("PENDING")).isEqualTo(TransactionStatus.PENDING);
        assertThat(TransactionStatus.valueOf("STUCK")).isEqualTo(TransactionStatus.STUCK);
        assertThat(TransactionStatus.valueOf("RECOVERING")).isEqualTo(TransactionStatus.RECOVERING);
        assertThat(TransactionStatus.valueOf("AWAITING_HUMAN")).isEqualTo(TransactionStatus.AWAITING_HUMAN);
        assertThat(TransactionStatus.valueOf("CONFIRMED")).isEqualTo(TransactionStatus.CONFIRMED);
        assertThat(TransactionStatus.valueOf("FINALIZED")).isEqualTo(TransactionStatus.FINALIZED);
        assertThat(TransactionStatus.valueOf("DROPPED")).isEqualTo(TransactionStatus.DROPPED);
        assertThat(TransactionStatus.valueOf("FAILED")).isEqualTo(TransactionStatus.FAILED);
        assertThat(TransactionStatus.valueOf("CANCELLED")).isEqualTo(TransactionStatus.CANCELLED);
    }

    @Test
    void shouldHaveThreeTerminalTransactionStatuses() {
        // given
        var terminalStatuses = Set.of(
                TransactionStatus.FINALIZED,
                TransactionStatus.FAILED,
                TransactionStatus.CANCELLED);

        // then
        assertThat(terminalStatuses).hasSize(3);
        assertThat(TransactionStatus.values()).contains(
                TransactionStatus.FINALIZED,
                TransactionStatus.FAILED,
                TransactionStatus.CANCELLED);
    }

    @Test
    void shouldHaveSevenStuckReasons() {
        // when
        var values = StuckReason.values();

        // then
        assertThat(values).hasSize(7);
    }

    @Test
    void shouldContainExpectedStuckReasons() {
        // when / then
        assertThat(StuckReason.valueOf("UNDERPRICED")).isEqualTo(StuckReason.UNDERPRICED);
        assertThat(StuckReason.valueOf("NONCE_GAP")).isEqualTo(StuckReason.NONCE_GAP);
        assertThat(StuckReason.valueOf("NONCE_CONSUMED")).isEqualTo(StuckReason.NONCE_CONSUMED);
        assertThat(StuckReason.valueOf("EXPIRED")).isEqualTo(StuckReason.EXPIRED);
        assertThat(StuckReason.valueOf("MEMPOOL_DROPPED")).isEqualTo(StuckReason.MEMPOOL_DROPPED);
        assertThat(StuckReason.valueOf("NOT_PROPAGATED")).isEqualTo(StuckReason.NOT_PROPAGATED);
        assertThat(StuckReason.valueOf("NOT_SEEN")).isEqualTo(StuckReason.NOT_SEEN);
    }

    @Test
    void shouldHaveFourRecoveryActions() {
        // when
        var values = RecoveryAction.values();

        // then
        assertThat(values).hasSize(4);
    }

    @Test
    void shouldContainExpectedRecoveryActions() {
        // when / then
        assertThat(RecoveryAction.valueOf("SPEED_UP")).isEqualTo(RecoveryAction.SPEED_UP);
        assertThat(RecoveryAction.valueOf("CANCEL")).isEqualTo(RecoveryAction.CANCEL);
        assertThat(RecoveryAction.valueOf("RESUBMIT")).isEqualTo(RecoveryAction.RESUBMIT);
        assertThat(RecoveryAction.valueOf("WAIT")).isEqualTo(RecoveryAction.WAIT);
    }

    @Test
    void shouldHaveFiveRecoveryOutcomes() {
        // when
        var values = RecoveryOutcome.values();

        // then
        assertThat(values).hasSize(5);
    }

    @Test
    void shouldContainExpectedRecoveryOutcomes() {
        // when / then
        assertThat(RecoveryOutcome.valueOf("REPLACEMENT_SUBMITTED"))
                .isEqualTo(RecoveryOutcome.REPLACEMENT_SUBMITTED);
        assertThat(RecoveryOutcome.valueOf("CONFIRMED")).isEqualTo(RecoveryOutcome.CONFIRMED);
        assertThat(RecoveryOutcome.valueOf("FAILED")).isEqualTo(RecoveryOutcome.FAILED);
        assertThat(RecoveryOutcome.valueOf("ESCALATED")).isEqualTo(RecoveryOutcome.ESCALATED);
        assertThat(RecoveryOutcome.valueOf("WAITING")).isEqualTo(RecoveryOutcome.WAITING);
    }

    @Test
    void shouldHaveThreeAddressTiers() {
        // when
        var values = AddressTier.values();

        // then
        assertThat(values).hasSize(3);
    }

    @Test
    void shouldContainExpectedAddressTiers() {
        // when / then
        assertThat(AddressTier.valueOf("HOT")).isEqualTo(AddressTier.HOT);
        assertThat(AddressTier.valueOf("PRIORITY")).isEqualTo(AddressTier.PRIORITY);
        assertThat(AddressTier.valueOf("COLD")).isEqualTo(AddressTier.COLD);
    }

    @Test
    void shouldHaveThreeAddressStatuses() {
        // when
        var values = AddressStatus.values();

        // then
        assertThat(values).hasSize(3);
    }

    @Test
    void shouldContainExpectedAddressStatuses() {
        // when / then
        assertThat(AddressStatus.valueOf("ACTIVE")).isEqualTo(AddressStatus.ACTIVE);
        assertThat(AddressStatus.valueOf("DRAINING")).isEqualTo(AddressStatus.DRAINING);
        assertThat(AddressStatus.valueOf("RETIRED")).isEqualTo(AddressStatus.RETIRED);
    }

    @Test
    void shouldHaveTwoChainFamilies() {
        // when
        var values = ChainFamily.values();

        // then
        assertThat(values).hasSize(2);
    }

    @Test
    void shouldContainExpectedChainFamilies() {
        // when / then
        assertThat(ChainFamily.valueOf("EVM")).isEqualTo(ChainFamily.EVM);
        assertThat(ChainFamily.valueOf("SOLANA")).isEqualTo(ChainFamily.SOLANA);
    }

    @Test
    void shouldHaveFourFeeUrgencies() {
        // when
        var values = FeeUrgency.values();

        // then
        assertThat(values).hasSize(4);
    }

    @Test
    void shouldContainExpectedFeeUrgencies() {
        // when / then
        assertThat(FeeUrgency.valueOf("SLOW")).isEqualTo(FeeUrgency.SLOW);
        assertThat(FeeUrgency.valueOf("MEDIUM")).isEqualTo(FeeUrgency.MEDIUM);
        assertThat(FeeUrgency.valueOf("FAST")).isEqualTo(FeeUrgency.FAST);
        assertThat(FeeUrgency.valueOf("URGENT")).isEqualTo(FeeUrgency.URGENT);
    }

    @Test
    void shouldHaveTwoSubmissionStrategies() {
        // when
        var values = SubmissionStrategy.values();

        // then
        assertThat(values).hasSize(2);
    }

    @Test
    void shouldContainExpectedSubmissionStrategies() {
        // when / then
        assertThat(SubmissionStrategy.valueOf("SEQUENTIAL")).isEqualTo(SubmissionStrategy.SEQUENTIAL);
        assertThat(SubmissionStrategy.valueOf("PIPELINED")).isEqualTo(SubmissionStrategy.PIPELINED);
    }

    @Test
    void shouldHaveThreeStuckSeverities() {
        // when
        var values = StuckSeverity.values();

        // then
        assertThat(values).hasSize(3);
    }

    @Test
    void shouldContainExpectedStuckSeverities() {
        // when / then
        assertThat(StuckSeverity.valueOf("LOW")).isEqualTo(StuckSeverity.LOW);
        assertThat(StuckSeverity.valueOf("MEDIUM")).isEqualTo(StuckSeverity.MEDIUM);
        assertThat(StuckSeverity.valueOf("HIGH")).isEqualTo(StuckSeverity.HIGH);
    }

    @Test
    void shouldHaveThreeApprovalActions() {
        // when
        var values = ApprovalAction.values();

        // then
        assertThat(values).hasSize(3);
    }

    @Test
    void shouldContainExpectedApprovalActions() {
        // when / then
        assertThat(ApprovalAction.valueOf("RETRY")).isEqualTo(ApprovalAction.RETRY);
        assertThat(ApprovalAction.valueOf("CANCEL")).isEqualTo(ApprovalAction.CANCEL);
        assertThat(ApprovalAction.valueOf("ABORT")).isEqualTo(ApprovalAction.ABORT);
    }

    @Test
    void shouldHaveThreeNonceAccountStatuses() {
        // when
        var values = NonceAccountStatus.values();

        // then
        assertThat(values).hasSize(3);
    }

    @Test
    void shouldContainExpectedNonceAccountStatuses() {
        // when / then
        assertThat(NonceAccountStatus.valueOf("AVAILABLE")).isEqualTo(NonceAccountStatus.AVAILABLE);
        assertThat(NonceAccountStatus.valueOf("IN_USE")).isEqualTo(NonceAccountStatus.IN_USE);
        assertThat(NonceAccountStatus.valueOf("EXHAUSTED")).isEqualTo(NonceAccountStatus.EXHAUSTED);
    }

    @Test
    void shouldThrowIllegalArgumentException_whenInvalidTransactionStatus() {
        // when / then
        assertThatThrownBy(() -> TransactionStatus.valueOf("INVALID"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldThrowIllegalArgumentException_whenInvalidStuckReason() {
        // when / then
        assertThatThrownBy(() -> StuckReason.valueOf("INVALID"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldThrowIllegalArgumentException_whenInvalidRecoveryAction() {
        // when / then
        assertThatThrownBy(() -> RecoveryAction.valueOf("INVALID"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
