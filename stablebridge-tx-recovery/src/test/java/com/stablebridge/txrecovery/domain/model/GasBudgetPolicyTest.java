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
        // given
        var txValueUsd = new BigDecimal("100");

        // when
        var budget = policy.calculateBudget(txValueUsd);

        // then — $100 * 1% = $1, below min $5, returns $5
        assertThat(budget).isEqualByComparingTo(new BigDecimal("5"));
    }

    @Test
    void shouldReturnPercentageBudget_whenBetweenMinAndMax() {
        // given
        var txValueUsd = new BigDecimal("10000");

        // when
        var budget = policy.calculateBudget(txValueUsd);

        // then — $10,000 * 1% = $100, between $5 and $500, returns $100
        assertThat(budget).isEqualByComparingTo(new BigDecimal("100"));
    }

    @Test
    void shouldReturnAbsoluteMaxUsd_whenPercentageBudgetAboveCeiling() {
        // given
        var txValueUsd = new BigDecimal("100000");

        // when
        var budget = policy.calculateBudget(txValueUsd);

        // then — $100,000 * 1% = $1,000, above max $500, returns $500
        assertThat(budget).isEqualByComparingTo(new BigDecimal("500"));
    }

    @Test
    void shouldReturnExactMin_whenPercentageBudgetEqualsMin() {
        // given
        var txValueUsd = new BigDecimal("500");

        // when
        var budget = policy.calculateBudget(txValueUsd);

        // then — $500 * 1% = $5, equals min $5, returns $5
        assertThat(budget).isEqualByComparingTo(new BigDecimal("5"));
    }

    @Test
    void shouldReturnExactMax_whenPercentageBudgetEqualsMax() {
        // given
        var txValueUsd = new BigDecimal("50000");

        // when
        var budget = policy.calculateBudget(txValueUsd);

        // then — $50,000 * 1% = $500, equals max $500, returns $500
        assertThat(budget).isEqualByComparingTo(new BigDecimal("500"));
    }

    @Test
    void shouldThrowNullPointerException_whenPercentageIsNull() {
        // when / then
        assertThatThrownBy(() -> GasBudgetPolicy.builder()
                .percentage(null)
                .absoluteMinUsd(new BigDecimal("5"))
                .absoluteMaxUsd(new BigDecimal("500"))
                .build())
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldThrowNullPointerException_whenAbsoluteMinUsdIsNull() {
        // when / then
        assertThatThrownBy(() -> GasBudgetPolicy.builder()
                .percentage(new BigDecimal("0.01"))
                .absoluteMinUsd(null)
                .absoluteMaxUsd(new BigDecimal("500"))
                .build())
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldThrowNullPointerException_whenAbsoluteMaxUsdIsNull() {
        // when / then
        assertThatThrownBy(() -> GasBudgetPolicy.builder()
                .percentage(new BigDecimal("0.01"))
                .absoluteMinUsd(new BigDecimal("5"))
                .absoluteMaxUsd(null)
                .build())
                .isInstanceOf(NullPointerException.class);
    }
}
