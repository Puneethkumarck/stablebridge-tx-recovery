package com.stablebridge.txrecovery.domain.recovery.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.stablebridge.txrecovery.domain.recovery.model.EscalationPolicy;
import com.stablebridge.txrecovery.domain.recovery.model.EscalationResult;
import com.stablebridge.txrecovery.domain.recovery.model.EscalationTier;
import com.stablebridge.txrecovery.domain.recovery.model.GasBudgetPolicy;

class EscalationPolicyEngineTest {

    private static final BigDecimal HIGH_VALUE_THRESHOLD = new BigDecimal("50000");

    private static final EscalationTier DEFAULT_TIER_0 = EscalationTier.builder()
            .level(0).stuckThreshold(Duration.ofMinutes(1))
            .gasMultiplier(new BigDecimal("1.0")).requiresHumanApproval(false)
            .description("Initial detection - wait").build();
    private static final EscalationTier DEFAULT_TIER_1 = EscalationTier.builder()
            .level(1).stuckThreshold(Duration.ofMinutes(3))
            .gasMultiplier(new BigDecimal("1.25")).requiresHumanApproval(false)
            .description("First speed-up").build();
    private static final EscalationTier DEFAULT_TIER_2 = EscalationTier.builder()
            .level(2).stuckThreshold(Duration.ofMinutes(10))
            .gasMultiplier(new BigDecimal("2.0")).requiresHumanApproval(false)
            .description("Second speed-up").build();
    private static final EscalationTier DEFAULT_TIER_3 = EscalationTier.builder()
            .level(3).stuckThreshold(Duration.ofMinutes(10))
            .gasMultiplier(new BigDecimal("3.0")).requiresHumanApproval(false)
            .description("Aggressive speed-up").build();
    private static final EscalationTier DEFAULT_TIER_4 = EscalationTier.builder()
            .level(4).stuckThreshold(Duration.ofMinutes(30))
            .gasMultiplier(new BigDecimal("3.0")).requiresHumanApproval(true)
            .description("Human escalation").build();

    private static final EscalationTier HV_TIER_0 = EscalationTier.builder()
            .level(0).stuckThreshold(Duration.ofMinutes(1))
            .gasMultiplier(new BigDecimal("1.0")).requiresHumanApproval(false)
            .description("Initial detection - wait").build();
    private static final EscalationTier HV_TIER_1 = EscalationTier.builder()
            .level(1).stuckThreshold(Duration.ofMinutes(3))
            .gasMultiplier(new BigDecimal("1.25")).requiresHumanApproval(false)
            .description("First speed-up").build();
    private static final EscalationTier HV_TIER_2 = EscalationTier.builder()
            .level(2).stuckThreshold(Duration.ofMinutes(5))
            .gasMultiplier(new BigDecimal("1.25")).requiresHumanApproval(true)
            .description("Human escalation for high-value").build();

    private static final EscalationPolicy DEFAULT_POLICY = EscalationPolicy.builder()
            .tiers(List.of(DEFAULT_TIER_0, DEFAULT_TIER_1, DEFAULT_TIER_2, DEFAULT_TIER_3, DEFAULT_TIER_4)).build();
    private static final EscalationPolicy HIGH_VALUE_POLICY = EscalationPolicy.builder()
            .tiers(List.of(HV_TIER_0, HV_TIER_1, HV_TIER_2)).build();

    private static final GasBudgetPolicy GAS_BUDGET_POLICY = GasBudgetPolicy.builder()
            .percentage(new BigDecimal("0.01"))
            .absoluteMinUsd(new BigDecimal("5"))
            .absoluteMaxUsd(new BigDecimal("500")).build();

    private final EscalationPolicyEngine engine = new EscalationPolicyEngine(
            DEFAULT_POLICY, HIGH_VALUE_POLICY, HIGH_VALUE_THRESHOLD,
            GAS_BUDGET_POLICY, Map.of());

    @Nested
    class PolicySelection {

        @Test
        void shouldSelectDefaultPolicy_whenTxValueBelowThreshold() {
            // given
            var txValue = new BigDecimal("10000");
            var stuckDuration = Duration.ofMinutes(5);
            var gasSpend = BigDecimal.ZERO;

            // when
            var result = engine.evaluate("ethereum", txValue, stuckDuration, gasSpend);

            // then
            var expected = EscalationResult.builder()
                    .selectedTier(DEFAULT_TIER_1)
                    .policyName("default")
                    .budgetExhausted(false)
                    .remainingBudget(new BigDecimal("100"))
                    .gasBudget(new BigDecimal("100"))
                    .build();
            assertThat(result)
                    .usingRecursiveComparison()
                    .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                    .isEqualTo(expected);
        }

        @Test
        void shouldSelectHighValuePolicy_whenTxValueAboveThreshold() {
            // given
            var txValue = new BigDecimal("100000");
            var stuckDuration = Duration.ofMinutes(4);
            var gasSpend = BigDecimal.ZERO;

            // when
            var result = engine.evaluate("ethereum", txValue, stuckDuration, gasSpend);

            // then
            var expected = EscalationResult.builder()
                    .selectedTier(HV_TIER_1)
                    .policyName("high-value")
                    .budgetExhausted(false)
                    .remainingBudget(new BigDecimal("500"))
                    .gasBudget(new BigDecimal("500"))
                    .build();
            assertThat(result)
                    .usingRecursiveComparison()
                    .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                    .isEqualTo(expected);
        }

        @Test
        void shouldSelectDefaultPolicy_whenTxValueEqualsThreshold() {
            // given
            var txValue = new BigDecimal("50000");
            var stuckDuration = Duration.ofMinutes(5);
            var gasSpend = BigDecimal.ZERO;

            // when
            var result = engine.evaluate("ethereum", txValue, stuckDuration, gasSpend);

            // then
            var expected = EscalationResult.builder()
                    .selectedTier(DEFAULT_TIER_1)
                    .policyName("default")
                    .budgetExhausted(false)
                    .remainingBudget(new BigDecimal("500"))
                    .gasBudget(new BigDecimal("500"))
                    .build();
            assertThat(result)
                    .usingRecursiveComparison()
                    .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                    .isEqualTo(expected);
        }

        @Test
        void shouldSelectChainOverride_whenChainHasOverride() {
            // given
            var customPolicy = EscalationPolicy.builder()
                    .tiers(List.of(DEFAULT_TIER_0, DEFAULT_TIER_1)).build();
            var engineWithOverride = new EscalationPolicyEngine(
                    DEFAULT_POLICY, HIGH_VALUE_POLICY, HIGH_VALUE_THRESHOLD,
                    GAS_BUDGET_POLICY, Map.of("polygon", customPolicy));
            var txValue = new BigDecimal("10000");
            var stuckDuration = Duration.ofMinutes(5);
            var gasSpend = BigDecimal.ZERO;

            // when
            var result = engineWithOverride.evaluate("polygon", txValue, stuckDuration, gasSpend);

            // then
            var expected = EscalationResult.builder()
                    .selectedTier(DEFAULT_TIER_1)
                    .policyName("chain-override:polygon")
                    .budgetExhausted(false)
                    .remainingBudget(new BigDecimal("100"))
                    .gasBudget(new BigDecimal("100"))
                    .build();
            assertThat(result)
                    .usingRecursiveComparison()
                    .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                    .isEqualTo(expected);
        }
    }

    @Nested
    class TierDetermination {

        @Test
        void shouldReturnFirstTier_whenStuckDurationBelowAllThresholds() {
            // given
            var txValue = new BigDecimal("10000");
            var stuckDuration = Duration.ofSeconds(30);
            var gasSpend = BigDecimal.ZERO;

            // when
            var result = engine.evaluate("ethereum", txValue, stuckDuration, gasSpend);

            // then
            var expected = EscalationResult.builder()
                    .selectedTier(DEFAULT_TIER_0)
                    .policyName("default")
                    .budgetExhausted(false)
                    .remainingBudget(new BigDecimal("100"))
                    .gasBudget(new BigDecimal("100"))
                    .build();
            assertThat(result)
                    .usingRecursiveComparison()
                    .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                    .isEqualTo(expected);
        }

        @Test
        void shouldReturnTier1_whenStuckBetween3And10Minutes() {
            // given
            var txValue = new BigDecimal("10000");
            var stuckDuration = Duration.ofMinutes(5);
            var gasSpend = BigDecimal.ZERO;

            // when
            var result = engine.evaluate("ethereum", txValue, stuckDuration, gasSpend);

            // then
            var expected = EscalationResult.builder()
                    .selectedTier(DEFAULT_TIER_1)
                    .policyName("default")
                    .budgetExhausted(false)
                    .remainingBudget(new BigDecimal("100"))
                    .gasBudget(new BigDecimal("100"))
                    .build();
            assertThat(result)
                    .usingRecursiveComparison()
                    .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                    .isEqualTo(expected);
        }

        @Test
        void shouldReturnTier2_whenStuckAtExactly10Minutes() {
            // given
            var txValue = new BigDecimal("10000");
            var stuckDuration = Duration.ofMinutes(10);
            var gasSpend = BigDecimal.ZERO;

            // when
            var result = engine.evaluate("ethereum", txValue, stuckDuration, gasSpend);

            // then
            var expected = EscalationResult.builder()
                    .selectedTier(DEFAULT_TIER_3)
                    .policyName("default")
                    .budgetExhausted(false)
                    .remainingBudget(new BigDecimal("100"))
                    .gasBudget(new BigDecimal("100"))
                    .build();
            assertThat(result)
                    .usingRecursiveComparison()
                    .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                    .isEqualTo(expected);
        }

        @Test
        void shouldReturnTier3_whenStuckBetween10And30Minutes() {
            // given
            var txValue = new BigDecimal("10000");
            var stuckDuration = Duration.ofMinutes(15);
            var gasSpend = BigDecimal.ZERO;

            // when
            var result = engine.evaluate("ethereum", txValue, stuckDuration, gasSpend);

            // then
            var expected = EscalationResult.builder()
                    .selectedTier(DEFAULT_TIER_3)
                    .policyName("default")
                    .budgetExhausted(false)
                    .remainingBudget(new BigDecimal("100"))
                    .gasBudget(new BigDecimal("100"))
                    .build();
            assertThat(result)
                    .usingRecursiveComparison()
                    .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                    .isEqualTo(expected);
        }

        @Test
        void shouldReturnHighestTier_whenStuckDurationExceedsAll() {
            // given
            var txValue = new BigDecimal("10000");
            var stuckDuration = Duration.ofMinutes(60);
            var gasSpend = BigDecimal.ZERO;

            // when
            var result = engine.evaluate("ethereum", txValue, stuckDuration, gasSpend);

            // then
            var expected = EscalationResult.builder()
                    .selectedTier(DEFAULT_TIER_4)
                    .policyName("default")
                    .budgetExhausted(false)
                    .remainingBudget(new BigDecimal("100"))
                    .gasBudget(new BigDecimal("100"))
                    .build();
            assertThat(result)
                    .usingRecursiveComparison()
                    .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                    .isEqualTo(expected);
        }

        @Test
        void shouldReturnExactThresholdMatch() {
            // given
            var txValue = new BigDecimal("10000");
            var stuckDuration = Duration.ofMinutes(3);
            var gasSpend = BigDecimal.ZERO;

            // when
            var result = engine.evaluate("ethereum", txValue, stuckDuration, gasSpend);

            // then
            var expected = EscalationResult.builder()
                    .selectedTier(DEFAULT_TIER_1)
                    .policyName("default")
                    .budgetExhausted(false)
                    .remainingBudget(new BigDecimal("100"))
                    .gasBudget(new BigDecimal("100"))
                    .build();
            assertThat(result)
                    .usingRecursiveComparison()
                    .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                    .isEqualTo(expected);
        }
    }

    @Nested
    class GasBudgetCalculation {

        @Test
        void shouldCalculateMinBudget_forLowValueTransaction() {
            // given
            var txValue = new BigDecimal("100");
            var stuckDuration = Duration.ofMinutes(5);
            var gasSpend = BigDecimal.ZERO;

            // when
            var result = engine.evaluate("ethereum", txValue, stuckDuration, gasSpend);

            // then
            var expected = EscalationResult.builder()
                    .selectedTier(DEFAULT_TIER_1)
                    .policyName("default")
                    .budgetExhausted(false)
                    .remainingBudget(new BigDecimal("5"))
                    .gasBudget(new BigDecimal("5"))
                    .build();
            assertThat(result)
                    .usingRecursiveComparison()
                    .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                    .isEqualTo(expected);
        }

        @Test
        void shouldCalculatePercentageBudget_forMidValueTransaction() {
            // given
            var txValue = new BigDecimal("10000");
            var stuckDuration = Duration.ofMinutes(5);
            var gasSpend = BigDecimal.ZERO;

            // when
            var result = engine.evaluate("ethereum", txValue, stuckDuration, gasSpend);

            // then
            var expected = EscalationResult.builder()
                    .selectedTier(DEFAULT_TIER_1)
                    .policyName("default")
                    .budgetExhausted(false)
                    .remainingBudget(new BigDecimal("100"))
                    .gasBudget(new BigDecimal("100"))
                    .build();
            assertThat(result)
                    .usingRecursiveComparison()
                    .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                    .isEqualTo(expected);
        }

        @Test
        void shouldCalculateMaxBudget_forHighValueTransaction() {
            // given
            var txValue = new BigDecimal("100000");
            var stuckDuration = Duration.ofMinutes(4);
            var gasSpend = BigDecimal.ZERO;

            // when
            var result = engine.evaluate("ethereum", txValue, stuckDuration, gasSpend);

            // then
            var expected = EscalationResult.builder()
                    .selectedTier(HV_TIER_1)
                    .policyName("high-value")
                    .budgetExhausted(false)
                    .remainingBudget(new BigDecimal("500"))
                    .gasBudget(new BigDecimal("500"))
                    .build();
            assertThat(result)
                    .usingRecursiveComparison()
                    .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                    .isEqualTo(expected);
        }

        @Test
        void shouldCalculateRemainingBudget() {
            // given
            var txValue = new BigDecimal("10000");
            var stuckDuration = Duration.ofMinutes(5);
            var gasSpend = new BigDecimal("30");

            // when
            var result = engine.evaluate("ethereum", txValue, stuckDuration, gasSpend);

            // then
            var expected = EscalationResult.builder()
                    .selectedTier(DEFAULT_TIER_1)
                    .policyName("default")
                    .budgetExhausted(false)
                    .remainingBudget(new BigDecimal("70"))
                    .gasBudget(new BigDecimal("100"))
                    .build();
            assertThat(result)
                    .usingRecursiveComparison()
                    .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                    .isEqualTo(expected);
        }
    }

    @Nested
    class BudgetExhaustion {

        @Test
        void shouldForceHumanEscalation_whenBudgetExhausted() {
            // given
            var txValue = new BigDecimal("10000");
            var stuckDuration = Duration.ofMinutes(5);
            var gasSpend = new BigDecimal("100");

            // when
            var result = engine.evaluate("ethereum", txValue, stuckDuration, gasSpend);

            // then
            var forcedHumanTier = DEFAULT_TIER_1.toBuilder().requiresHumanApproval(true).build();
            var expected = EscalationResult.builder()
                    .selectedTier(forcedHumanTier)
                    .policyName("default")
                    .budgetExhausted(true)
                    .remainingBudget(BigDecimal.ZERO)
                    .gasBudget(new BigDecimal("100"))
                    .build();
            assertThat(result)
                    .usingRecursiveComparison()
                    .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                    .isEqualTo(expected);
        }

        @Test
        void shouldNotForceHumanEscalation_whenBudgetRemaining() {
            // given
            var txValue = new BigDecimal("10000");
            var stuckDuration = Duration.ofMinutes(5);
            var gasSpend = new BigDecimal("50");

            // when
            var result = engine.evaluate("ethereum", txValue, stuckDuration, gasSpend);

            // then
            var expected = EscalationResult.builder()
                    .selectedTier(DEFAULT_TIER_1)
                    .policyName("default")
                    .budgetExhausted(false)
                    .remainingBudget(new BigDecimal("50"))
                    .gasBudget(new BigDecimal("100"))
                    .build();
            assertThat(result)
                    .usingRecursiveComparison()
                    .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                    .isEqualTo(expected);
        }

        @Test
        void shouldForceHumanEscalation_whenGasSpendExceedsBudget() {
            // given
            var txValue = new BigDecimal("10000");
            var stuckDuration = Duration.ofMinutes(5);
            var gasSpend = new BigDecimal("150");

            // when
            var result = engine.evaluate("ethereum", txValue, stuckDuration, gasSpend);

            // then
            var forcedHumanTier = DEFAULT_TIER_1.toBuilder().requiresHumanApproval(true).build();
            var expected = EscalationResult.builder()
                    .selectedTier(forcedHumanTier)
                    .policyName("default")
                    .budgetExhausted(true)
                    .remainingBudget(new BigDecimal("-50"))
                    .gasBudget(new BigDecimal("100"))
                    .build();
            assertThat(result)
                    .usingRecursiveComparison()
                    .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                    .isEqualTo(expected);
        }

        @Test
        void shouldPreserveHumanApproval_whenTierAlreadyRequiresIt() {
            // given
            var txValue = new BigDecimal("10000");
            var stuckDuration = Duration.ofMinutes(60);
            var gasSpend = new BigDecimal("100");

            // when
            var result = engine.evaluate("ethereum", txValue, stuckDuration, gasSpend);

            // then
            var expected = EscalationResult.builder()
                    .selectedTier(DEFAULT_TIER_4)
                    .policyName("default")
                    .budgetExhausted(true)
                    .remainingBudget(BigDecimal.ZERO)
                    .gasBudget(new BigDecimal("100"))
                    .build();
            assertThat(result)
                    .usingRecursiveComparison()
                    .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                    .isEqualTo(expected);
        }
    }

    @Nested
    class HighValueTierDetermination {

        @Test
        void shouldReturnHighValueTier2_whenStuckOver5Minutes() {
            // given
            var txValue = new BigDecimal("100000");
            var stuckDuration = Duration.ofMinutes(10);
            var gasSpend = BigDecimal.ZERO;

            // when
            var result = engine.evaluate("ethereum", txValue, stuckDuration, gasSpend);

            // then
            var expected = EscalationResult.builder()
                    .selectedTier(HV_TIER_2)
                    .policyName("high-value")
                    .budgetExhausted(false)
                    .remainingBudget(new BigDecimal("500"))
                    .gasBudget(new BigDecimal("500"))
                    .build();
            assertThat(result)
                    .usingRecursiveComparison()
                    .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                    .isEqualTo(expected);
        }

        @Test
        void shouldReturnHighValueTier0_whenStuckUnder1Minute() {
            // given
            var txValue = new BigDecimal("100000");
            var stuckDuration = Duration.ofSeconds(30);
            var gasSpend = BigDecimal.ZERO;

            // when
            var result = engine.evaluate("ethereum", txValue, stuckDuration, gasSpend);

            // then
            var expected = EscalationResult.builder()
                    .selectedTier(HV_TIER_0)
                    .policyName("high-value")
                    .budgetExhausted(false)
                    .remainingBudget(new BigDecimal("500"))
                    .gasBudget(new BigDecimal("500"))
                    .build();
            assertThat(result)
                    .usingRecursiveComparison()
                    .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                    .isEqualTo(expected);
        }
    }
}
