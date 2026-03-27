package com.stablebridge.txrecovery.infrastructure.client.evm;

import static com.stablebridge.txrecovery.infrastructure.client.evm.EvmFeeOracleFixtures.SOME_BASE_FEE_WEI;
import static com.stablebridge.txrecovery.infrastructure.client.evm.EvmFeeOracleFixtures.SOME_BASE_PROPERTIES;
import static com.stablebridge.txrecovery.infrastructure.client.evm.EvmFeeOracleFixtures.SOME_BLOCK_TIME;
import static com.stablebridge.txrecovery.infrastructure.client.evm.EvmFeeOracleFixtures.SOME_CHAIN;
import static com.stablebridge.txrecovery.infrastructure.client.evm.EvmFeeOracleFixtures.SOME_EIP1559_TRANSACTION;
import static com.stablebridge.txrecovery.infrastructure.client.evm.EvmFeeOracleFixtures.SOME_ETHEREUM_PROPERTIES;
import static com.stablebridge.txrecovery.infrastructure.client.evm.EvmFeeOracleFixtures.SOME_FEE_HISTORY;
import static com.stablebridge.txrecovery.infrastructure.client.evm.EvmFeeOracleFixtures.SOME_HIGH_BASE_FEE_HISTORY;
import static com.stablebridge.txrecovery.infrastructure.client.evm.EvmFeeOracleFixtures.SOME_LEGACY_TRANSACTION;
import static com.stablebridge.txrecovery.infrastructure.client.evm.EvmFeeOracleFixtures.SOME_LOW_FEE_TRANSACTION;
import static com.stablebridge.txrecovery.infrastructure.client.evm.EvmFeeOracleFixtures.SOME_MAX_FEE_CAP_GWEI;
import static com.stablebridge.txrecovery.infrastructure.client.evm.EvmFeeOracleFixtures.SOME_POLYGON_FEE_HISTORY;
import static com.stablebridge.txrecovery.infrastructure.client.evm.EvmFeeOracleFixtures.SOME_POLYGON_PROPERTIES;
import static com.stablebridge.txrecovery.infrastructure.client.evm.EvmFeeOracleFixtures.SOME_REWARD_PERCENTILES;
import static com.stablebridge.txrecovery.infrastructure.client.evm.EvmFeeOracleFixtures.SOME_SAFETY_CAP_WEI;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.stablebridge.txrecovery.domain.recovery.model.FeeEstimate;
import com.stablebridge.txrecovery.domain.recovery.model.FeeUrgency;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class EvmFeeOracleTest {

    private static final BigDecimal BASE_FEE_MULTIPLIER = new BigDecimal("1.125");
    private static final MathContext MATH_CONTEXT = MathContext.DECIMAL128;

    @Mock
    private EvmRpcClient rpcClient;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    private ObjectMapper objectMapper;
    private EvmFeeOracle oracle;

    @BeforeEach
    void setUp() {
        objectMapper = JsonMapper.builder().build();
        oracle = new EvmFeeOracle(rpcClient, SOME_ETHEREUM_PROPERTIES, redisTemplate, objectMapper);
    }

    private void stubCacheMiss(FeeUrgency urgency) {
        given(redisTemplate.opsForHash()).willReturn(hashOperations);
        given(hashOperations.get("str:gas:cache:ethereum", urgency.name())).willReturn(null);
    }

    private FeeEstimate buildExpectedEstimate(FeeUrgency urgency, int exponent, BigDecimal priorityFeeWei) {
        var expectedMaxFee = SOME_BASE_FEE_WEI
                .multiply(BASE_FEE_MULTIPLIER.pow(exponent, MATH_CONTEXT), MATH_CONTEXT)
                .setScale(0, RoundingMode.CEILING);
        var expectedPriorityFee = priorityFeeWei.min(expectedMaxFee);
        return FeeEstimate.builder()
                .maxFeePerGas(expectedMaxFee)
                .maxPriorityFeePerGas(expectedPriorityFee)
                .estimatedCost(expectedMaxFee)
                .denomination("wei")
                .urgency(urgency)
                .details(Map.of(
                        "baseFee", SOME_BASE_FEE_WEI.toPlainString(),
                        "maxFeePerGas", expectedMaxFee.toPlainString(),
                        "priorityFee", expectedPriorityFee.toPlainString(),
                        "multiplier", BASE_FEE_MULTIPLIER.pow(exponent, MATH_CONTEXT).toPlainString(),
                        "safetyCapWei", SOME_SAFETY_CAP_WEI.toPlainString()))
                .build();
    }

    @Nested
    class ChainValidation {

        @Test
        void shouldThrowWhenEstimateCalledWithWrongChain() {
            // when/then
            assertThatThrownBy(() -> oracle.estimate("base", FeeUrgency.FAST))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Oracle for chain ethereum cannot serve chain base");
        }

        @Test
        void shouldThrowWhenEstimateReplacementCalledWithWrongChain() {
            // when/then
            assertThatThrownBy(() -> oracle.estimateReplacement("polygon", "0xoriginal", 1))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Oracle for chain ethereum cannot serve chain polygon");
        }
    }

    @Nested
    class Estimate {

        @Nested
        class SlowUrgency {

            @Test
            void shouldCalculateMaxFeeWithExponent1() {
                // given
                stubCacheMiss(FeeUrgency.SLOW);
                given(rpcClient.feeHistory(10, "latest", SOME_REWARD_PERCENTILES))
                        .willReturn(SOME_FEE_HISTORY);

                // when
                var result = oracle.estimate(SOME_CHAIN, FeeUrgency.SLOW);

                // then
                var expected = buildExpectedEstimate(FeeUrgency.SLOW, 1, new BigDecimal("1500000000"));
                assertThat(result).usingRecursiveComparison().isEqualTo(expected);
            }
        }

        @Nested
        class MediumUrgency {

            @Test
            void shouldCalculateMaxFeeWithExponent2() {
                // given
                stubCacheMiss(FeeUrgency.MEDIUM);
                given(rpcClient.feeHistory(10, "latest", SOME_REWARD_PERCENTILES))
                        .willReturn(SOME_FEE_HISTORY);

                // when
                var result = oracle.estimate(SOME_CHAIN, FeeUrgency.MEDIUM);

                // then
                var expected = buildExpectedEstimate(FeeUrgency.MEDIUM, 2, new BigDecimal("2000000000"));
                assertThat(result).usingRecursiveComparison().isEqualTo(expected);
            }
        }

        @Nested
        class FastUrgency {

            @Test
            void shouldCalculateMaxFeeWithExponent3() {
                // given
                stubCacheMiss(FeeUrgency.FAST);
                given(rpcClient.feeHistory(10, "latest", SOME_REWARD_PERCENTILES))
                        .willReturn(SOME_FEE_HISTORY);

                // when
                var result = oracle.estimate(SOME_CHAIN, FeeUrgency.FAST);

                // then
                var expected = buildExpectedEstimate(FeeUrgency.FAST, 3, new BigDecimal("3000000000"));
                assertThat(result).usingRecursiveComparison().isEqualTo(expected);
            }
        }

        @Nested
        class UrgentUrgency {

            @Test
            void shouldCalculateMaxFeeWithExponent5() {
                // given
                stubCacheMiss(FeeUrgency.URGENT);
                given(rpcClient.feeHistory(10, "latest", SOME_REWARD_PERCENTILES))
                        .willReturn(SOME_FEE_HISTORY);

                // when
                var result = oracle.estimate(SOME_CHAIN, FeeUrgency.URGENT);

                // then
                var expected = buildExpectedEstimate(FeeUrgency.URGENT, 5, new BigDecimal("100000000000"));
                assertThat(result).usingRecursiveComparison().isEqualTo(expected);
            }
        }
    }

    @Nested
    class SafetyCapEnforcement {

        @Test
        void shouldClampMaxFeeToSafetyCapForBase() {
            // given
            var baseOracle = new EvmFeeOracle(rpcClient, SOME_BASE_PROPERTIES, redisTemplate, objectMapper);
            var baseCapWei = new BigDecimal("5000000000");
            given(redisTemplate.opsForHash()).willReturn(hashOperations);
            given(hashOperations.get("str:gas:cache:base", "FAST")).willReturn(null);
            given(rpcClient.feeHistory(10, "latest", SOME_REWARD_PERCENTILES))
                    .willReturn(SOME_HIGH_BASE_FEE_HISTORY);

            // when
            var result = baseOracle.estimate("base", FeeUrgency.FAST);

            // then
            var expected = FeeEstimate.builder()
                    .maxFeePerGas(baseCapWei)
                    .maxPriorityFeePerGas(new BigDecimal("3000000000").min(baseCapWei))
                    .estimatedCost(baseCapWei)
                    .denomination("wei")
                    .urgency(FeeUrgency.FAST)
                    .details(Map.of(
                            "baseFee", new BigDecimal("80000000000").toPlainString(),
                            "maxFeePerGas", baseCapWei.toPlainString(),
                            "priorityFee", new BigDecimal("3000000000").min(baseCapWei).toPlainString(),
                            "multiplier", BASE_FEE_MULTIPLIER.pow(3, MATH_CONTEXT).toPlainString(),
                            "safetyCapWei", baseCapWei.toPlainString()))
                    .build();
            assertThat(result).usingRecursiveComparison().isEqualTo(expected);
        }

        @Test
        void shouldNotClampWhenBelowSafetyCap() {
            // given
            stubCacheMiss(FeeUrgency.SLOW);
            given(rpcClient.feeHistory(10, "latest", SOME_REWARD_PERCENTILES))
                    .willReturn(SOME_FEE_HISTORY);

            // when
            var result = oracle.estimate(SOME_CHAIN, FeeUrgency.SLOW);

            // then
            assertThat(result.maxFeePerGas()).isLessThan(SOME_SAFETY_CAP_WEI);
        }

        @Test
        void shouldClampPolygonFeeToSafetyCap() {
            // given
            var polygonOracle = new EvmFeeOracle(
                    rpcClient, SOME_POLYGON_PROPERTIES, redisTemplate, objectMapper);
            var polygonCapWei = new BigDecimal("500000000000");
            given(redisTemplate.opsForHash()).willReturn(hashOperations);
            given(hashOperations.get("str:gas:cache:polygon", "URGENT")).willReturn(null);
            given(rpcClient.feeHistory(10, "latest", SOME_REWARD_PERCENTILES))
                    .willReturn(SOME_POLYGON_FEE_HISTORY);

            // when
            var result = polygonOracle.estimate("polygon", FeeUrgency.URGENT);

            // then
            var expected = FeeEstimate.builder()
                    .maxFeePerGas(polygonCapWei)
                    .maxPriorityFeePerGas(new BigDecimal("20000000000").min(polygonCapWei))
                    .estimatedCost(polygonCapWei)
                    .denomination("wei")
                    .urgency(FeeUrgency.URGENT)
                    .details(Map.of(
                            "baseFee", new BigDecimal("1000000000000").toPlainString(),
                            "maxFeePerGas", polygonCapWei.toPlainString(),
                            "priorityFee", new BigDecimal("20000000000").min(polygonCapWei).toPlainString(),
                            "multiplier", BASE_FEE_MULTIPLIER.pow(5, MATH_CONTEXT).toPlainString(),
                            "safetyCapWei", polygonCapWei.toPlainString()))
                    .build();
            assertThat(result).usingRecursiveComparison().isEqualTo(expected);
        }
    }

    @Nested
    class EstimateReplacement {

        @Test
        void shouldCalculateReplacementFeeWithMinimum10PercentBump() {
            // given
            stubCacheMiss(FeeUrgency.FAST);
            given(rpcClient.getTransactionByHash("0xoriginal"))
                    .willReturn(Optional.of(SOME_EIP1559_TRANSACTION));
            given(rpcClient.feeHistory(10, "latest", SOME_REWARD_PERCENTILES))
                    .willReturn(SOME_FEE_HISTORY);

            // when
            var result = oracle.estimateReplacement(SOME_CHAIN, "0xoriginal", 1);

            // then
            var originalMaxFee = new BigDecimal("80000000000");
            var bumpedOriginal = originalMaxFee
                    .multiply(new BigDecimal("1.1"), MATH_CONTEXT)
                    .setScale(0, RoundingMode.CEILING);
            var fastMaxFee = SOME_BASE_FEE_WEI
                    .multiply(BASE_FEE_MULTIPLIER.pow(3, MATH_CONTEXT), MATH_CONTEXT)
                    .setScale(0, RoundingMode.CEILING);
            var escalation = new BigDecimal("1.1");
            var replacementFee = bumpedOriginal.max(fastMaxFee)
                    .multiply(escalation, MATH_CONTEXT)
                    .setScale(0, RoundingMode.CEILING);
            var cappedFee = replacementFee.min(SOME_SAFETY_CAP_WEI);

            var originalPriorityFee = new BigDecimal("2000000000");
            var bumpedPriorityFee = originalPriorityFee
                    .multiply(new BigDecimal("1.1"), MATH_CONTEXT)
                    .multiply(escalation, MATH_CONTEXT)
                    .setScale(0, RoundingMode.CEILING);
            var fastPriorityFee = new BigDecimal("3000000000").min(fastMaxFee);
            var finalPriorityFee = bumpedPriorityFee.min(cappedFee).max(fastPriorityFee).min(cappedFee);

            var expected = FeeEstimate.builder()
                    .maxFeePerGas(cappedFee)
                    .maxPriorityFeePerGas(finalPriorityFee)
                    .estimatedCost(cappedFee)
                    .denomination("wei")
                    .urgency(FeeUrgency.URGENT)
                    .details(Map.of(
                            "originalMaxFee", originalMaxFee.toPlainString(),
                            "bumpedOriginal", bumpedOriginal.toPlainString(),
                            "currentFastFee", fastMaxFee.toPlainString(),
                            "escalationMultiplier", escalation.toPlainString(),
                            "maxFeePerGas", cappedFee.toPlainString(),
                            "priorityFee", finalPriorityFee.toPlainString(),
                            "safetyCapWei", SOME_SAFETY_CAP_WEI.toPlainString(),
                            "attemptNumber", "1"))
                    .build();
            assertThat(result).usingRecursiveComparison().isEqualTo(expected);
        }

        @Test
        void shouldThrowWhenOriginalTransactionNotFound() {
            // given
            given(rpcClient.getTransactionByHash("0xnonexistent")).willReturn(Optional.empty());

            // when/then
            assertThatThrownBy(() -> oracle.estimateReplacement(SOME_CHAIN, "0xnonexistent", 1))
                    .isInstanceOf(EvmRpcException.class)
                    .hasMessageContaining("Original transaction not found");
        }

        @Test
        void shouldThrowWhenAttemptNumberIsZero() {
            // when/then
            assertThatThrownBy(() -> oracle.estimateReplacement(SOME_CHAIN, "0xoriginal", 0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("attemptNumber must be >= 1");
        }

        @Test
        void shouldThrowWhenAttemptNumberIsNegative() {
            // when/then
            assertThatThrownBy(() -> oracle.estimateReplacement(SOME_CHAIN, "0xoriginal", -1))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("attemptNumber must be >= 1");
        }

        @Test
        void shouldApplyEscalationMultiplierBasedOnAttemptNumber() {
            // given
            stubCacheMiss(FeeUrgency.FAST);
            given(rpcClient.getTransactionByHash("0xoriginal"))
                    .willReturn(Optional.of(SOME_LOW_FEE_TRANSACTION));
            given(rpcClient.feeHistory(10, "latest", SOME_REWARD_PERCENTILES))
                    .willReturn(SOME_FEE_HISTORY);

            // when
            var result1 = oracle.estimateReplacement(SOME_CHAIN, "0xoriginal", 1);
            given(hashOperations.get("str:gas:cache:ethereum", "FAST")).willReturn(null);
            var result3 = oracle.estimateReplacement(SOME_CHAIN, "0xoriginal", 3);

            // then
            assertThat(result3.maxFeePerGas()).isGreaterThan(result1.maxFeePerGas());
        }

        @Test
        void shouldFallbackToGasPriceWhenMaxFeePerGasIsNull() {
            // given
            stubCacheMiss(FeeUrgency.FAST);
            given(rpcClient.getTransactionByHash("0xlegacy"))
                    .willReturn(Optional.of(SOME_LEGACY_TRANSACTION));
            given(rpcClient.feeHistory(10, "latest", SOME_REWARD_PERCENTILES))
                    .willReturn(SOME_FEE_HISTORY);

            // when
            var result = oracle.estimateReplacement(SOME_CHAIN, "0xlegacy", 1);

            // then
            var originalMaxFee = new BigDecimal("20000000000");
            var bumpedOriginal = originalMaxFee
                    .multiply(new BigDecimal("1.1"), MATH_CONTEXT)
                    .setScale(0, RoundingMode.CEILING);
            var fastMaxFee = SOME_BASE_FEE_WEI
                    .multiply(BASE_FEE_MULTIPLIER.pow(3, MATH_CONTEXT), MATH_CONTEXT)
                    .setScale(0, RoundingMode.CEILING);
            var escalation = new BigDecimal("1.1");
            var replacementFee = bumpedOriginal.max(fastMaxFee)
                    .multiply(escalation, MATH_CONTEXT)
                    .setScale(0, RoundingMode.CEILING);
            var cappedFee = replacementFee.min(SOME_SAFETY_CAP_WEI);

            var bumpedPriorityFee = BigDecimal.ZERO
                    .multiply(new BigDecimal("1.1"), MATH_CONTEXT)
                    .multiply(escalation, MATH_CONTEXT)
                    .setScale(0, RoundingMode.CEILING);
            var fastPriorityFee = new BigDecimal("3000000000").min(fastMaxFee);
            var finalPriorityFee = bumpedPriorityFee.min(cappedFee).max(fastPriorityFee).min(cappedFee);

            var expected = FeeEstimate.builder()
                    .maxFeePerGas(cappedFee)
                    .maxPriorityFeePerGas(finalPriorityFee)
                    .estimatedCost(cappedFee)
                    .denomination("wei")
                    .urgency(FeeUrgency.URGENT)
                    .details(Map.of(
                            "originalMaxFee", originalMaxFee.toPlainString(),
                            "bumpedOriginal", bumpedOriginal.toPlainString(),
                            "currentFastFee", fastMaxFee.toPlainString(),
                            "escalationMultiplier", escalation.toPlainString(),
                            "maxFeePerGas", cappedFee.toPlainString(),
                            "priorityFee", finalPriorityFee.toPlainString(),
                            "safetyCapWei", SOME_SAFETY_CAP_WEI.toPlainString(),
                            "attemptNumber", "1"))
                    .build();
            assertThat(result).usingRecursiveComparison().isEqualTo(expected);
        }
    }

    @Nested
    class UrgencyMapping {

        @Test
        void shouldMapSlowToExponent1() {
            // when/then
            assertThat(EvmFeeOracle.urgencyExponent(FeeUrgency.SLOW)).isEqualTo(1);
        }

        @Test
        void shouldMapMediumToExponent2() {
            // when/then
            assertThat(EvmFeeOracle.urgencyExponent(FeeUrgency.MEDIUM)).isEqualTo(2);
        }

        @Test
        void shouldMapFastToExponent3() {
            // when/then
            assertThat(EvmFeeOracle.urgencyExponent(FeeUrgency.FAST)).isEqualTo(3);
        }

        @Test
        void shouldMapUrgentToExponent5() {
            // when/then
            assertThat(EvmFeeOracle.urgencyExponent(FeeUrgency.URGENT)).isEqualTo(5);
        }

        @Test
        void shouldMapSlowToPercentileIndex0() {
            // when/then
            assertThat(EvmFeeOracle.percentileIndex(FeeUrgency.SLOW)).isEqualTo(0);
        }

        @Test
        void shouldMapMediumToPercentileIndex1() {
            // when/then
            assertThat(EvmFeeOracle.percentileIndex(FeeUrgency.MEDIUM)).isEqualTo(1);
        }

        @Test
        void shouldMapFastToPercentileIndex2() {
            // when/then
            assertThat(EvmFeeOracle.percentileIndex(FeeUrgency.FAST)).isEqualTo(2);
        }

        @Test
        void shouldMapUrgentToPercentileIndex3() {
            // when/then
            assertThat(EvmFeeOracle.percentileIndex(FeeUrgency.URGENT)).isEqualTo(3);
        }
    }

    @Nested
    class FeeHistoryParsing {

        @Test
        void shouldExtractLatestBaseFee() {
            // given
            var feeHistory = EvmFeeHistory.builder()
                    .oldestBlock("0xa")
                    .baseFeePerGas(List.of("0x3b9aca00", "0x4a817c800", "0x77359400"))
                    .gasUsedRatio(List.of(0.5f, 0.5f))
                    .reward(List.of())
                    .build();

            // when
            var result = oracle.latestBaseFee(feeHistory);

            // then
            assertThat(result).isEqualByComparingTo(new BigDecimal("2000000000"));
        }

        @Test
        void shouldThrowWhenBaseFeeListIsEmpty() {
            // given
            var feeHistory = EvmFeeHistory.builder()
                    .oldestBlock("0xa")
                    .baseFeePerGas(List.of())
                    .gasUsedRatio(List.of())
                    .reward(List.of())
                    .build();

            // when/then
            assertThatThrownBy(() -> oracle.latestBaseFee(feeHistory))
                    .isInstanceOf(EvmRpcException.class)
                    .hasMessageContaining("empty baseFeePerGas");
        }

        @Test
        void shouldCalculateMedianRewardForPercentile() {
            // given
            var feeHistory = EvmFeeHistory.builder()
                    .oldestBlock("0xa")
                    .baseFeePerGas(List.of(SOME_BASE_FEE_WEI.toPlainString()))
                    .gasUsedRatio(List.of(0.5f, 0.6f, 0.7f))
                    .reward(List.of(
                            List.of("0x59682f00", "0x77359400"),
                            List.of("0x3b9aca00", "0x5d21dba00"),
                            List.of("0xb2d05e00", "0x174876e800")))
                    .build();

            // when
            var result = oracle.medianRewardForPercentile(feeHistory, 0);

            // then
            assertThat(result).isEqualByComparingTo(new BigDecimal("1500000000"));
        }

        @Test
        void shouldReturnZeroWhenRewardsEmpty() {
            // given
            var feeHistory = EvmFeeHistory.builder()
                    .oldestBlock("0xa")
                    .baseFeePerGas(List.of(SOME_BASE_FEE_WEI.toPlainString()))
                    .gasUsedRatio(List.of())
                    .reward(List.of())
                    .build();

            // when
            var result = oracle.medianRewardForPercentile(feeHistory, 0);

            // then
            assertThat(result).isEqualByComparingTo(BigDecimal.ZERO);
        }
    }

    @Nested
    class MaxFeeCalculation {

        @Test
        void shouldCalculateMaxFeePerGasWithCorrectFormula() {
            // given
            var baseFee = new BigDecimal("1000000000");

            // when
            var result = oracle.calculateMaxFeePerGas(baseFee, 3);

            // then
            var expected = baseFee
                    .multiply(BASE_FEE_MULTIPLIER.pow(3, MATH_CONTEXT), MATH_CONTEXT)
                    .setScale(0, RoundingMode.CEILING);
            assertThat(result).isEqualByComparingTo(expected);
        }

        @Test
        void shouldCapFeeToSafetyLimit() {
            // when
            var result = oracle.capToSafetyLimit(SOME_SAFETY_CAP_WEI.multiply(BigDecimal.TEN));

            // then
            assertThat(result).isEqualByComparingTo(SOME_SAFETY_CAP_WEI);
        }

        @Test
        void shouldNotCapFeeWhenBelowLimit() {
            // given
            var fee = new BigDecimal("1000000000");

            // when
            var result = oracle.capToSafetyLimit(fee);

            // then
            assertThat(result).isEqualByComparingTo(fee);
        }
    }

    @Nested
    class CacheBehavior {

        @Test
        void shouldReturnCachedEstimateOnHit() {
            // given
            var cachedEstimate = FeeEstimate.builder()
                    .maxFeePerGas(new BigDecimal("1125000000"))
                    .maxPriorityFeePerGas(new BigDecimal("1500000000"))
                    .estimatedCost(new BigDecimal("1125000000"))
                    .denomination("wei")
                    .urgency(FeeUrgency.SLOW)
                    .details(Map.of("baseFee", "1000000000"))
                    .build();
            given(redisTemplate.opsForHash()).willReturn(hashOperations);
            given(hashOperations.get("str:gas:cache:ethereum", "SLOW"))
                    .willReturn(objectMapper.writeValueAsString(cachedEstimate));

            // when
            var result = oracle.estimate(SOME_CHAIN, FeeUrgency.SLOW);

            // then
            assertThat(result).usingRecursiveComparison().isEqualTo(cachedEstimate);
            then(rpcClient).should(never()).feeHistory(10, "latest", SOME_REWARD_PERCENTILES);
        }

        @Test
        void shouldFetchFromRpcOnCacheMiss() {
            // given
            stubCacheMiss(FeeUrgency.SLOW);
            given(rpcClient.feeHistory(10, "latest", SOME_REWARD_PERCENTILES))
                    .willReturn(SOME_FEE_HISTORY);

            // when
            var result = oracle.estimate(SOME_CHAIN, FeeUrgency.SLOW);

            // then
            assertThat(result.maxFeePerGas()).isPositive();
            then(rpcClient).should().feeHistory(10, "latest", SOME_REWARD_PERCENTILES);
        }

        @Test
        void shouldSetCacheTtlAfterFetch() {
            // given
            stubCacheMiss(FeeUrgency.SLOW);
            given(rpcClient.feeHistory(10, "latest", SOME_REWARD_PERCENTILES))
                    .willReturn(SOME_FEE_HISTORY);
            given(redisTemplate.expire("str:gas:cache:ethereum", SOME_BLOCK_TIME.toMillis(), TimeUnit.MILLISECONDS))
                    .willReturn(true);

            // when
            oracle.estimate(SOME_CHAIN, FeeUrgency.SLOW);

            // then
            then(redisTemplate).should()
                    .expire("str:gas:cache:ethereum", SOME_BLOCK_TIME.toMillis(), TimeUnit.MILLISECONDS);
        }
    }

    @Nested
    class ChainPropertiesAccessor {

        @Test
        void shouldReturnChainName() {
            // when/then
            assertThat(oracle.getChain()).isEqualTo(SOME_CHAIN);
        }
    }

    @Nested
    class EvmChainPropertiesTest {

        @Test
        void shouldConvertEthereumGweiToWei() {
            // when
            var result = SOME_ETHEREUM_PROPERTIES.maxFeeCapWei();

            // then
            assertThat(result).isEqualByComparingTo(new BigDecimal("200000000000"));
        }

        @Test
        void shouldConvertBaseGweiToWei() {
            // when
            var result = SOME_BASE_PROPERTIES.maxFeeCapWei();

            // then
            assertThat(result).isEqualByComparingTo(new BigDecimal("5000000000"));
        }

        @Test
        void shouldConvertPolygonGweiToWei() {
            // when
            var result = SOME_POLYGON_PROPERTIES.maxFeeCapWei();

            // then
            assertThat(result).isEqualByComparingTo(new BigDecimal("500000000000"));
        }

        @Test
        void shouldThrowWhenChainIsNull() {
            // when/then
            assertThatThrownBy(() -> EvmChainProperties.builder()
                    .chain(null)
                    .maxFeeCapGwei(SOME_MAX_FEE_CAP_GWEI)
                    .blockTime(SOME_BLOCK_TIME)
                    .build())
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        void shouldThrowWhenMaxFeeCapGweiIsNull() {
            // when/then
            assertThatThrownBy(() -> EvmChainProperties.builder()
                    .chain(SOME_CHAIN)
                    .maxFeeCapGwei(null)
                    .blockTime(SOME_BLOCK_TIME)
                    .build())
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        void shouldThrowWhenBlockTimeIsNull() {
            // when/then
            assertThatThrownBy(() -> EvmChainProperties.builder()
                    .chain(SOME_CHAIN)
                    .maxFeeCapGwei(SOME_MAX_FEE_CAP_GWEI)
                    .blockTime(null)
                    .build())
                    .isInstanceOf(NullPointerException.class);
        }
    }
}
