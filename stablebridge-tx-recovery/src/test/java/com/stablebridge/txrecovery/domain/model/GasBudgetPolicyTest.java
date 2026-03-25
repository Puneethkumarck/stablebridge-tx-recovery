package com.stablebridge.txrecovery.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class GasBudgetPolicyTest {

    private final GasBudgetPolicy policy = GasBudgetPolicy.builder()
            .percentage(new BigDecimal("0.01"))
            .absoluteMinUsd(new BigDecimal("5"))
            .absoluteMaxUsd(new BigDecimal("500"))
            .build();

    @Test
    void shouldReturnAbsoluteMinUsd_whenPercentageBudgetBelowFloor() {
        var txValueUsd = new BigDecimal("100");

        var budget = policy.calculateBudget(txValueUsd);

        assertThat(budget).isEqualByComparingTo(new BigDecimal("5"));
    }

    @Test
    void shouldReturnPercentageBudget_whenBetweenMinAndMax() {
        var txValueUsd = new BigDecimal("10000");

        var budget = policy.calculateBudget(txValueUsd);

        assertThat(budget).isEqualByComparingTo(new BigDecimal("100"));
    }

    @Test
    void shouldReturnAbsoluteMaxUsd_whenPercentageBudgetAboveCeiling() {
        var txValueUsd = new BigDecimal("100000");

        var budget = policy.calculateBudget(txValueUsd);

        assertThat(budget).isEqualByComparingTo(new BigDecimal("500"));
    }

    @Test
    void shouldReturnExactMin_whenPercentageBudgetEqualsMin() {
        var txValueUsd = new BigDecimal("500");

        var budget = policy.calculateBudget(txValueUsd);

        assertThat(budget).isEqualByComparingTo(new BigDecimal("5"));
    }

    @Test
    void shouldReturnExactMax_whenPercentageBudgetEqualsMax() {
        var txValueUsd = new BigDecimal("50000");

        var budget = policy.calculateBudget(txValueUsd);

        assertThat(budget).isEqualByComparingTo(new BigDecimal("500"));
    }

    @Test
    void shouldThrowNullPointerException_whenPercentageIsNull() {
        assertThatThrownBy(() -> GasBudgetPolicy.builder()
                .percentage(null)
                .absoluteMinUsd(new BigDecimal("5"))
                .absoluteMaxUsd(new BigDecimal("500"))
                .build())
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldThrowNullPointerException_whenAbsoluteMinUsdIsNull() {
        assertThatThrownBy(() -> GasBudgetPolicy.builder()
                .percentage(new BigDecimal("0.01"))
                .absoluteMinUsd(null)
                .absoluteMaxUsd(new BigDecimal("500"))
                .build())
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldThrowNullPointerException_whenAbsoluteMaxUsdIsNull() {
        assertThatThrownBy(() -> GasBudgetPolicy.builder()
                .percentage(new BigDecimal("0.01"))
                .absoluteMinUsd(new BigDecimal("5"))
                .absoluteMaxUsd(null)
                .build())
                .isInstanceOf(NullPointerException.class);
    }
}
