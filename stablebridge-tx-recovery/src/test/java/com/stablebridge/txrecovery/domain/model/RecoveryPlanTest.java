package com.stablebridge.txrecovery.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Duration;

import org.junit.jupiter.api.Test;

class RecoveryPlanTest {

    @Test
    void shouldCreateSpeedUpPlan() {
        var fee = FeeEstimate.builder()
                .estimatedCost(new BigDecimal("0.005"))
                .denomination("ETH")
                .urgency(FeeUrgency.FAST)
                .build();

        var plan = RecoveryPlan.SpeedUp.builder()
                .originalTxHash("0xabc123")
                .newFee(fee)
                .build();

        var expected = RecoveryPlan.SpeedUp.builder()
                .originalTxHash("0xabc123")
                .newFee(fee)
                .build();

        assertThat(plan).usingRecursiveComparison().isEqualTo(expected);
    }

    @Test
    void shouldCreateCancelPlan() {
        var plan = RecoveryPlan.Cancel.builder()
                .originalTxHash("0xdef456")
                .build();

        var expected = RecoveryPlan.Cancel.builder()
                .originalTxHash("0xdef456")
                .build();

        assertThat(plan).usingRecursiveComparison().isEqualTo(expected);
    }

    @Test
    void shouldCreateResubmitPlan() {
        var plan = RecoveryPlan.Resubmit.builder()
                .originalTxHash("0xghi789")
                .build();

        var expected = RecoveryPlan.Resubmit.builder()
                .originalTxHash("0xghi789")
                .build();

        assertThat(plan).usingRecursiveComparison().isEqualTo(expected);
    }

    @Test
    void shouldCreateWaitPlan() {
        var plan = RecoveryPlan.Wait.builder()
                .estimatedClearance(Duration.ofMinutes(5))
                .reason("Mempool congestion expected to clear")
                .build();

        var expected = RecoveryPlan.Wait.builder()
                .estimatedClearance(Duration.ofMinutes(5))
                .reason("Mempool congestion expected to clear")
                .build();

        assertThat(plan).usingRecursiveComparison().isEqualTo(expected);
    }

    @Test
    void shouldCreateWaitPlan_whenEstimatedClearanceIsNull() {
        var plan = RecoveryPlan.Wait.builder()
                .reason("Unknown clearance time")
                .build();

        var expected = RecoveryPlan.Wait.builder()
                .reason("Unknown clearance time")
                .build();

        assertThat(plan).usingRecursiveComparison().isEqualTo(expected);
    }

    @Test
    void shouldPatternMatchAllVariants() {
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

        assertThat(describeAction(speedUp)).isEqualTo("speed-up");
        assertThat(describeAction(cancel)).isEqualTo("cancel");
        assertThat(describeAction(resubmit)).isEqualTo("resubmit");
        assertThat(describeAction(wait)).isEqualTo("wait");
    }

    @Test
    void shouldThrowNullPointerException_whenSpeedUpOriginalTxHashIsNull() {
        var fee = FeeEstimate.builder()
                .estimatedCost(new BigDecimal("0.005"))
                .denomination("ETH")
                .urgency(FeeUrgency.FAST)
                .build();

        assertThatThrownBy(() -> RecoveryPlan.SpeedUp.builder()
                .originalTxHash(null)
                .newFee(fee)
                .build())
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldThrowNullPointerException_whenSpeedUpNewFeeIsNull() {
        assertThatThrownBy(() -> RecoveryPlan.SpeedUp.builder()
                .originalTxHash("0xabc")
                .newFee(null)
                .build())
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldThrowNullPointerException_whenCancelOriginalTxHashIsNull() {
        assertThatThrownBy(() -> RecoveryPlan.Cancel.builder()
                .originalTxHash(null)
                .build())
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldThrowNullPointerException_whenResubmitOriginalTxHashIsNull() {
        assertThatThrownBy(() -> RecoveryPlan.Resubmit.builder()
                .originalTxHash(null)
                .build())
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldThrowNullPointerException_whenWaitReasonIsNull() {
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
