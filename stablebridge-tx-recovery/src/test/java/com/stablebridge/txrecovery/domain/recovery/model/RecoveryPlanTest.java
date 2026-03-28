package com.stablebridge.txrecovery.domain.recovery.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Duration;

import org.junit.jupiter.api.Test;

class RecoveryPlanTest {

    @Test
    void shouldModifyNewFeeViaToBuilder() {
        // given
        var originalFee = FeeEstimate.builder()
                .estimatedCost(new BigDecimal("0.005"))
                .denomination("ETH")
                .urgency(FeeUrgency.FAST)
                .build();

        var plan = RecoveryPlan.SpeedUp.builder()
                .originalTxHash("0xabc123")
                .newFee(originalFee)
                .build();

        var updatedFee = FeeEstimate.builder()
                .estimatedCost(new BigDecimal("0.010"))
                .denomination("ETH")
                .urgency(FeeUrgency.URGENT)
                .build();

        // when
        var modified = plan.toBuilder().newFee(updatedFee).build();

        // then
        var expected = RecoveryPlan.SpeedUp.builder()
                .originalTxHash("0xabc123")
                .newFee(updatedFee)
                .build();
        assertThat(modified).usingRecursiveComparison().isEqualTo(expected);
    }

    @Test
    void shouldModifyCancelOriginalTxHashViaToBuilder() {
        // given
        var plan = RecoveryPlan.Cancel.builder()
                .originalTxHash("0xdef456")
                .build();

        // when
        var modified = plan.toBuilder().originalTxHash("0xnew789").build();

        // then
        assertThat(modified.originalTxHash()).isEqualTo("0xnew789");
    }

    @Test
    void shouldModifyResubmitOriginalTxHashViaToBuilder() {
        // given
        var plan = RecoveryPlan.Resubmit.builder()
                .originalTxHash("0xghi789")
                .build();

        // when
        var modified = plan.toBuilder().originalTxHash("0xnew123").build();

        // then
        assertThat(modified.originalTxHash()).isEqualTo("0xnew123");
    }

    @Test
    void shouldAllowNullEstimatedClearanceInWait() {
        // when
        var plan = RecoveryPlan.Wait.builder()
                .reason("Unknown clearance time")
                .build();

        // then
        assertThat(plan.estimatedClearance()).isNull();
        assertThat(plan.reason()).isEqualTo("Unknown clearance time");
    }

    @Test
    void shouldModifyWaitEstimatedClearanceViaToBuilder() {
        // given
        var plan = RecoveryPlan.Wait.builder()
                .estimatedClearance(Duration.ofMinutes(5))
                .reason("Mempool congestion expected to clear")
                .build();

        // when
        var modified = plan.toBuilder().estimatedClearance(Duration.ofMinutes(15)).build();

        // then
        var expected = RecoveryPlan.Wait.builder()
                .estimatedClearance(Duration.ofMinutes(15))
                .reason("Mempool congestion expected to clear")
                .build();
        assertThat(modified).usingRecursiveComparison().isEqualTo(expected);
    }

    @Test
    void shouldPatternMatchAllVariants() {
        // given
        var fee = FeeEstimate.builder()
                .estimatedCost(new BigDecimal("0.005"))
                .denomination("ETH")
                .urgency(FeeUrgency.FAST)
                .build();

        RecoveryPlan speedUp = RecoveryPlan.SpeedUp.builder()
                .originalTxHash("0x1")
                .newFee(fee)
                .build();
        RecoveryPlan cancel = RecoveryPlan.Cancel.builder()
                .originalTxHash("0x2")
                .build();
        RecoveryPlan resubmit = RecoveryPlan.Resubmit.builder()
                .originalTxHash("0x3")
                .build();
        RecoveryPlan wait = RecoveryPlan.Wait.builder()
                .reason("congestion")
                .build();

        // when / then
        assertThat(describeAction(speedUp)).isEqualTo("speed-up");
        assertThat(describeAction(cancel)).isEqualTo("cancel");
        assertThat(describeAction(resubmit)).isEqualTo("resubmit");
        assertThat(describeAction(wait)).isEqualTo("wait");
    }

    @Test
    void shouldThrowNullPointerException_whenSpeedUpOriginalTxHashIsNull() {
        // given
        var fee = FeeEstimate.builder()
                .estimatedCost(new BigDecimal("0.005"))
                .denomination("ETH")
                .urgency(FeeUrgency.FAST)
                .build();

        // when / then
        assertThatThrownBy(() -> RecoveryPlan.SpeedUp.builder()
                .originalTxHash(null)
                .newFee(fee)
                .build())
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldThrowNullPointerException_whenSpeedUpNewFeeIsNull() {
        // when / then
        assertThatThrownBy(() -> RecoveryPlan.SpeedUp.builder()
                .originalTxHash("0xabc")
                .newFee(null)
                .build())
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldThrowNullPointerException_whenCancelOriginalTxHashIsNull() {
        // when / then
        assertThatThrownBy(() -> RecoveryPlan.Cancel.builder()
                .originalTxHash(null)
                .build())
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldThrowNullPointerException_whenResubmitOriginalTxHashIsNull() {
        // when / then
        assertThatThrownBy(() -> RecoveryPlan.Resubmit.builder()
                .originalTxHash(null)
                .build())
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldThrowNullPointerException_whenWaitReasonIsNull() {
        // when / then
        assertThatThrownBy(() -> RecoveryPlan.Wait.builder()
                .reason(null)
                .build())
                .isInstanceOf(NullPointerException.class);
    }

    private String describeAction(RecoveryPlan plan) {
        return switch (plan) {
            case RecoveryPlan.SpeedUp _ -> "speed-up";
            case RecoveryPlan.Cancel _ -> "cancel";
            case RecoveryPlan.Resubmit _ -> "resubmit";
            case RecoveryPlan.Wait _ -> "wait";
        };
    }
}
