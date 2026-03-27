package com.stablebridge.txrecovery.infrastructure.client.evm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Duration;
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

    private static final String CHAIN = "ethereum";
    private static final BigDecimal MAX_FEE_CAP_GWEI = new BigDecimal("200");
    private static final Duration BLOCK_TIME = Duration.ofSeconds(12);
    private static final BigDecimal BASE_FEE_MULTIPLIER = new BigDecimal("1.125");
    private static final MathContext MATH_CONTEXT = MathContext.DECIMAL128;
    private static final String BASE_FEE_HEX = "0x3b9aca00";
    private static final BigDecimal BASE_FEE_WEI = new BigDecimal("1000000000");
    private static final BigDecimal SAFETY_CAP_WEI = MAX_FEE_CAP_GWEI.multiply(BigDecimal.valueOf(1_000_000_000L));

    @Mock private EvmRpcClient rpcClient;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private HashOperations<String, Object, Object> hashOperations;

    private ObjectMapper objectMapper;
    private EvmFeeOracle oracle;

    @BeforeEach
    void setUp() {
        objectMapper = JsonMapper.builder().build();
        var properties = EvmChainProperties.builder().chain(CHAIN).maxFeeCapGwei(MAX_FEE_CAP_GWEI).blockTime(BLOCK_TIME).build();
        oracle = new EvmFeeOracle(rpcClient, properties, redisTemplate, objectMapper);
    }

    private EvmFeeHistory standardFeeHistory() {
        return new EvmFeeHistory("0xa", List.of(BASE_FEE_HEX, BASE_FEE_HEX, BASE_FEE_HEX), List.of(0.5f, 0.5f),
                List.of(List.of("0x59682f00", "0x77359400", "0xb2d05e00", "0x174876e800"), List.of("0x59682f00", "0x77359400", "0xb2d05e00", "0x174876e800")));
    }

    private void stubCacheMissForUrgency(FeeUrgency urgency) {
        given(redisTemplate.opsForHash()).willReturn(hashOperations);
        given(hashOperations.get("str:gas:cache:ethereum", urgency.name())).willReturn(null);
    }

    @Nested class Estimate {
        @Nested class SlowUrgency { @Test void shouldCalculateMaxFeeWithExponent1() {
            stubCacheMissForUrgency(FeeUrgency.SLOW);
            given(rpcClient.feeHistory(10, "latest", List.of(25.0f, 50.0f, 75.0f, 95.0f))).willReturn(standardFeeHistory());
            var result = oracle.estimate(CHAIN, FeeUrgency.SLOW);
            var expectedMaxFee = BASE_FEE_WEI.multiply(BASE_FEE_MULTIPLIER.pow(1, MATH_CONTEXT), MATH_CONTEXT).setScale(0, RoundingMode.CEILING);
            var expectedPriorityFee = new BigDecimal("1500000000").min(expectedMaxFee);
            var expected = FeeEstimate.builder().maxFeePerGas(expectedMaxFee).maxPriorityFeePerGas(expectedPriorityFee).estimatedCost(expectedMaxFee).denomination("wei").urgency(FeeUrgency.SLOW)
                    .details(Map.of("baseFee", BASE_FEE_WEI.toPlainString(), "maxFeePerGas", expectedMaxFee.toPlainString(), "priorityFee", expectedPriorityFee.toPlainString(), "multiplier", BASE_FEE_MULTIPLIER.pow(1, MATH_CONTEXT).toPlainString(), "safetyCapWei", SAFETY_CAP_WEI.toPlainString())).build();
            assertThat(result).usingRecursiveComparison().isEqualTo(expected);
        }}
        @Nested class MediumUrgency { @Test void shouldCalculateMaxFeeWithExponent2() {
            stubCacheMissForUrgency(FeeUrgency.MEDIUM);
            given(rpcClient.feeHistory(10, "latest", List.of(25.0f, 50.0f, 75.0f, 95.0f))).willReturn(standardFeeHistory());
            var result = oracle.estimate(CHAIN, FeeUrgency.MEDIUM);
            var expectedMaxFee = BASE_FEE_WEI.multiply(BASE_FEE_MULTIPLIER.pow(2, MATH_CONTEXT), MATH_CONTEXT).setScale(0, RoundingMode.CEILING);
            var expectedPriorityFee = new BigDecimal("2000000000").min(expectedMaxFee);
            var expected = FeeEstimate.builder().maxFeePerGas(expectedMaxFee).maxPriorityFeePerGas(expectedPriorityFee).estimatedCost(expectedMaxFee).denomination("wei").urgency(FeeUrgency.MEDIUM)
                    .details(Map.of("baseFee", BASE_FEE_WEI.toPlainString(), "maxFeePerGas", expectedMaxFee.toPlainString(), "priorityFee", expectedPriorityFee.toPlainString(), "multiplier", BASE_FEE_MULTIPLIER.pow(2, MATH_CONTEXT).toPlainString(), "safetyCapWei", SAFETY_CAP_WEI.toPlainString())).build();
            assertThat(result).usingRecursiveComparison().isEqualTo(expected);
        }}
        @Nested class FastUrgency { @Test void shouldCalculateMaxFeeWithExponent3() {
            stubCacheMissForUrgency(FeeUrgency.FAST);
            given(rpcClient.feeHistory(10, "latest", List.of(25.0f, 50.0f, 75.0f, 95.0f))).willReturn(standardFeeHistory());
            var result = oracle.estimate(CHAIN, FeeUrgency.FAST);
            var expectedMaxFee = BASE_FEE_WEI.multiply(BASE_FEE_MULTIPLIER.pow(3, MATH_CONTEXT), MATH_CONTEXT).setScale(0, RoundingMode.CEILING);
            var expectedPriorityFee = new BigDecimal("3000000000").min(expectedMaxFee);
            var expected = FeeEstimate.builder().maxFeePerGas(expectedMaxFee).maxPriorityFeePerGas(expectedPriorityFee).estimatedCost(expectedMaxFee).denomination("wei").urgency(FeeUrgency.FAST)
                    .details(Map.of("baseFee", BASE_FEE_WEI.toPlainString(), "maxFeePerGas", expectedMaxFee.toPlainString(), "priorityFee", expectedPriorityFee.toPlainString(), "multiplier", BASE_FEE_MULTIPLIER.pow(3, MATH_CONTEXT).toPlainString(), "safetyCapWei", SAFETY_CAP_WEI.toPlainString())).build();
            assertThat(result).usingRecursiveComparison().isEqualTo(expected);
        }}
        @Nested class UrgentUrgency { @Test void shouldCalculateMaxFeeWithExponent5() {
            stubCacheMissForUrgency(FeeUrgency.URGENT);
            given(rpcClient.feeHistory(10, "latest", List.of(25.0f, 50.0f, 75.0f, 95.0f))).willReturn(standardFeeHistory());
            var result = oracle.estimate(CHAIN, FeeUrgency.URGENT);
            var expectedMaxFee = BASE_FEE_WEI.multiply(BASE_FEE_MULTIPLIER.pow(5, MATH_CONTEXT), MATH_CONTEXT).setScale(0, RoundingMode.CEILING);
            var expectedPriorityFee = new BigDecimal("100000000000").min(expectedMaxFee);
            var expected = FeeEstimate.builder().maxFeePerGas(expectedMaxFee).maxPriorityFeePerGas(expectedPriorityFee).estimatedCost(expectedMaxFee).denomination("wei").urgency(FeeUrgency.URGENT)
                    .details(Map.of("baseFee", BASE_FEE_WEI.toPlainString(), "maxFeePerGas", expectedMaxFee.toPlainString(), "priorityFee", expectedPriorityFee.toPlainString(), "multiplier", BASE_FEE_MULTIPLIER.pow(5, MATH_CONTEXT).toPlainString(), "safetyCapWei", SAFETY_CAP_WEI.toPlainString())).build();
            assertThat(result).usingRecursiveComparison().isEqualTo(expected);
        }}
    }

    @Nested class SafetyCapEnforcement {
        @Test void shouldClampMaxFeeToSafetyCapForBase() {
            var lowCapOracle = new EvmFeeOracle(rpcClient, EvmChainProperties.builder().chain("base").maxFeeCapGwei(new BigDecimal("5")).blockTime(Duration.ofSeconds(2)).build(), redisTemplate, objectMapper);
            var feeHistory = new EvmFeeHistory("0xa", List.of("0x12a05f2000", "0x12a05f2000", "0x12a05f2000"), List.of(0.8f, 0.8f), List.of(List.of("0x59682f00", "0x77359400", "0xb2d05e00", "0x174876e800"), List.of("0x59682f00", "0x77359400", "0xb2d05e00", "0x174876e800")));
            var baseCacheOps = mock(HashOperations.class); given(redisTemplate.opsForHash()).willReturn(baseCacheOps); given(baseCacheOps.get("str:gas:cache:base", "FAST")).willReturn(null);
            given(rpcClient.feeHistory(10, "latest", List.of(25.0f, 50.0f, 75.0f, 95.0f))).willReturn(feeHistory);
            var result = lowCapOracle.estimate("base", FeeUrgency.FAST);
            assertThat(result.maxFeePerGas()).isLessThanOrEqualTo(new BigDecimal("5000000000"));
            assertThat(result.maxPriorityFeePerGas()).isLessThanOrEqualTo(new BigDecimal("5000000000"));
        }
        @Test void shouldNotClampWhenBelowSafetyCap() {
            stubCacheMissForUrgency(FeeUrgency.SLOW); given(rpcClient.feeHistory(10, "latest", List.of(25.0f, 50.0f, 75.0f, 95.0f))).willReturn(standardFeeHistory());
            assertThat(oracle.estimate(CHAIN, FeeUrgency.SLOW).maxFeePerGas()).isLessThan(SAFETY_CAP_WEI);
        }
        @Test void shouldClampPolygonFeeToSafetyCap() {
            var polygonOracle = new EvmFeeOracle(rpcClient, EvmChainProperties.builder().chain("polygon").maxFeeCapGwei(new BigDecimal("500")).blockTime(Duration.ofSeconds(2)).build(), redisTemplate, objectMapper);
            var feeHistory = new EvmFeeHistory("0xa", List.of("0xe8d4a51000", "0xe8d4a51000", "0xe8d4a51000"), List.of(0.9f, 0.9f), List.of(List.of("0x174876e800", "0x2540be400", "0x3b9aca00", "0x4a817c800"), List.of("0x174876e800", "0x2540be400", "0x3b9aca00", "0x4a817c800")));
            var polygonCacheOps = mock(HashOperations.class); given(redisTemplate.opsForHash()).willReturn(polygonCacheOps); given(polygonCacheOps.get("str:gas:cache:polygon", "URGENT")).willReturn(null);
            given(rpcClient.feeHistory(10, "latest", List.of(25.0f, 50.0f, 75.0f, 95.0f))).willReturn(feeHistory);
            var result = polygonOracle.estimate("polygon", FeeUrgency.URGENT);
            assertThat(result.maxFeePerGas()).isLessThanOrEqualTo(new BigDecimal("500000000000"));
            assertThat(result.maxPriorityFeePerGas()).isLessThanOrEqualTo(new BigDecimal("500000000000"));
        }
    }

    @Nested class EstimateReplacement {
        @Test void shouldCalculateReplacementFeeWithMinimum10PercentBump() {
            stubCacheMissForUrgency(FeeUrgency.FAST);
            given(rpcClient.getTransactionByHash("0xoriginal")).willReturn(Optional.of(new EvmTransaction("0xoriginal", "0x1", "0xblock", "0xa", "0x0", "0xsender", "0xreceiver", "0x0", "0x5208", null, "0x12a05f2000", "0x77359400", "0x", "0x2")));
            given(rpcClient.feeHistory(10, "latest", List.of(25.0f, 50.0f, 75.0f, 95.0f))).willReturn(standardFeeHistory());
            var result = oracle.estimateReplacement(CHAIN, "0xoriginal", 1);
            assertThat(result.maxFeePerGas()).isGreaterThanOrEqualTo(new BigDecimal("80000000000").multiply(new BigDecimal("1.1"), MATH_CONTEXT).setScale(0, RoundingMode.CEILING));
            assertThat(result.urgency()).isEqualTo(FeeUrgency.URGENT); assertThat(result.denomination()).isEqualTo("wei");
            assertThat(result.details()).containsKey("originalMaxFee").containsKey("escalationMultiplier");
        }
        @Test void shouldThrowWhenOriginalTransactionNotFound() {
            given(rpcClient.getTransactionByHash("0xnonexistent")).willReturn(Optional.empty());
            assertThatThrownBy(() -> oracle.estimateReplacement(CHAIN, "0xnonexistent", 1)).isInstanceOf(EvmRpcException.class).hasMessageContaining("Original transaction not found");
        }
        @Test void shouldApplyEscalationMultiplierBasedOnAttemptNumber() {
            stubCacheMissForUrgency(FeeUrgency.FAST);
            given(rpcClient.getTransactionByHash("0xoriginal")).willReturn(Optional.of(new EvmTransaction("0xoriginal", "0x1", "0xblock", "0xa", "0x0", "0xsender", "0xreceiver", "0x0", "0x5208", null, "0x3b9aca00", "0x59682f00", "0x", "0x2")));
            given(rpcClient.feeHistory(10, "latest", List.of(25.0f, 50.0f, 75.0f, 95.0f))).willReturn(standardFeeHistory());
            var result1 = oracle.estimateReplacement(CHAIN, "0xoriginal", 1);
            given(hashOperations.get("str:gas:cache:ethereum", "FAST")).willReturn(null);
            var result3 = oracle.estimateReplacement(CHAIN, "0xoriginal", 3);
            assertThat(result3.maxFeePerGas()).isGreaterThan(result1.maxFeePerGas());
        }
        @Test void shouldFallbackToGasPriceWhenMaxFeePerGasIsNull() {
            stubCacheMissForUrgency(FeeUrgency.FAST);
            given(rpcClient.getTransactionByHash("0xlegacy")).willReturn(Optional.of(new EvmTransaction("0xlegacy", "0x1", "0xblock", "0xa", "0x0", "0xsender", "0xreceiver", "0x0", "0x5208", "0x4a817c800", null, null, "0x", "0x0")));
            given(rpcClient.feeHistory(10, "latest", List.of(25.0f, 50.0f, 75.0f, 95.0f))).willReturn(standardFeeHistory());
            var result = oracle.estimateReplacement(CHAIN, "0xlegacy", 1);
            assertThat(result.maxFeePerGas()).isPositive(); assertThat(result.details().get("originalMaxFee")).isEqualTo("20000000000");
        }
    }

    @Nested class UrgencyMapping {
        @Test void shouldMapSlowToExponent1() { assertThat(EvmFeeOracle.urgencyExponent(FeeUrgency.SLOW)).isEqualTo(1); }
        @Test void shouldMapMediumToExponent2() { assertThat(EvmFeeOracle.urgencyExponent(FeeUrgency.MEDIUM)).isEqualTo(2); }
        @Test void shouldMapFastToExponent3() { assertThat(EvmFeeOracle.urgencyExponent(FeeUrgency.FAST)).isEqualTo(3); }
        @Test void shouldMapUrgentToExponent5() { assertThat(EvmFeeOracle.urgencyExponent(FeeUrgency.URGENT)).isEqualTo(5); }
        @Test void shouldMapSlowToPercentileIndex0() { assertThat(EvmFeeOracle.percentileIndex(FeeUrgency.SLOW)).isEqualTo(0); }
        @Test void shouldMapMediumToPercentileIndex1() { assertThat(EvmFeeOracle.percentileIndex(FeeUrgency.MEDIUM)).isEqualTo(1); }
        @Test void shouldMapFastToPercentileIndex2() { assertThat(EvmFeeOracle.percentileIndex(FeeUrgency.FAST)).isEqualTo(2); }
        @Test void shouldMapUrgentToPercentileIndex3() { assertThat(EvmFeeOracle.percentileIndex(FeeUrgency.URGENT)).isEqualTo(3); }
    }

    @Nested class FeeHistoryParsing {
        @Test void shouldExtractLatestBaseFee() { assertThat(oracle.latestBaseFee(new EvmFeeHistory("0xa", List.of("0x3b9aca00", "0x4a817c800", "0x77359400"), List.of(0.5f, 0.5f), List.of()))).isEqualByComparingTo(new BigDecimal("2000000000")); }
        @Test void shouldThrowWhenBaseFeeListIsEmpty() { assertThatThrownBy(() -> oracle.latestBaseFee(new EvmFeeHistory("0xa", List.of(), List.of(), List.of()))).isInstanceOf(EvmRpcException.class).hasMessageContaining("empty baseFeePerGas"); }
        @Test void shouldCalculateMedianRewardForPercentile() { assertThat(oracle.medianRewardForPercentile(new EvmFeeHistory("0xa", List.of(BASE_FEE_HEX), List.of(0.5f, 0.6f, 0.7f), List.of(List.of("0x59682f00", "0x77359400"), List.of("0x3b9aca00", "0x5d21dba00"), List.of("0xb2d05e00", "0x174876e800"))), 0)).isEqualByComparingTo(new BigDecimal("1500000000")); }
        @Test void shouldReturnZeroWhenRewardsEmpty() { assertThat(oracle.medianRewardForPercentile(new EvmFeeHistory("0xa", List.of(BASE_FEE_HEX), List.of(), List.of()), 0)).isEqualByComparingTo(BigDecimal.ZERO); }
    }

    @Nested class MaxFeeCalculation {
        @Test void shouldCalculateMaxFeePerGasWithCorrectFormula() { assertThat(oracle.calculateMaxFeePerGas(new BigDecimal("1000000000"), 3)).isEqualByComparingTo(new BigDecimal("1000000000").multiply(BASE_FEE_MULTIPLIER.pow(3, MATH_CONTEXT), MATH_CONTEXT).setScale(0, RoundingMode.CEILING)); }
        @Test void shouldCapFeeToSafetyLimit() { assertThat(oracle.capToSafetyLimit(SAFETY_CAP_WEI.multiply(BigDecimal.TEN))).isEqualByComparingTo(SAFETY_CAP_WEI); }
        @Test void shouldNotCapFeeWhenBelowLimit() { assertThat(oracle.capToSafetyLimit(new BigDecimal("1000000000"))).isEqualByComparingTo(new BigDecimal("1000000000")); }
    }

    @Nested class CacheBehavior {
        @Test void shouldReturnCachedEstimateOnHit() {
            var cachedEstimate = FeeEstimate.builder().maxFeePerGas(new BigDecimal("1125000000")).maxPriorityFeePerGas(new BigDecimal("1500000000")).estimatedCost(new BigDecimal("1125000000")).denomination("wei").urgency(FeeUrgency.SLOW).details(Map.of("baseFee", "1000000000")).build();
            given(redisTemplate.opsForHash()).willReturn(hashOperations); given(hashOperations.get("str:gas:cache:ethereum", "SLOW")).willReturn(objectMapper.writeValueAsString(cachedEstimate));
            assertThat(oracle.estimate(CHAIN, FeeUrgency.SLOW)).usingRecursiveComparison().isEqualTo(cachedEstimate);
            then(rpcClient).should(never()).feeHistory(10, "latest", List.of(25.0f, 50.0f, 75.0f, 95.0f));
        }
        @Test void shouldFetchFromRpcOnCacheMiss() {
            stubCacheMissForUrgency(FeeUrgency.SLOW); given(rpcClient.feeHistory(10, "latest", List.of(25.0f, 50.0f, 75.0f, 95.0f))).willReturn(standardFeeHistory());
            assertThat(oracle.estimate(CHAIN, FeeUrgency.SLOW).maxFeePerGas()).isPositive();
            then(rpcClient).should().feeHistory(10, "latest", List.of(25.0f, 50.0f, 75.0f, 95.0f));
        }
        @Test void shouldSetCacheTtlAfterFetch() {
            stubCacheMissForUrgency(FeeUrgency.SLOW); given(rpcClient.feeHistory(10, "latest", List.of(25.0f, 50.0f, 75.0f, 95.0f))).willReturn(standardFeeHistory());
            given(redisTemplate.expire("str:gas:cache:ethereum", BLOCK_TIME.toMillis(), TimeUnit.MILLISECONDS)).willReturn(true);
            oracle.estimate(CHAIN, FeeUrgency.SLOW);
            then(redisTemplate).should().expire("str:gas:cache:ethereum", BLOCK_TIME.toMillis(), TimeUnit.MILLISECONDS);
        }
    }

    @Nested class ChainPropertiesAccessor { @Test void shouldReturnChainName() { assertThat(oracle.getChain()).isEqualTo(CHAIN); } }

    @Nested class EvmChainPropertiesTest {
        @Test void shouldConvertEthereumGweiToWei() { assertThat(EvmChainProperties.builder().chain("ethereum").maxFeeCapGwei(new BigDecimal("200")).blockTime(Duration.ofSeconds(12)).build().maxFeeCapWei()).isEqualByComparingTo(new BigDecimal("200000000000")); }
        @Test void shouldConvertBaseGweiToWei() { assertThat(EvmChainProperties.builder().chain("base").maxFeeCapGwei(new BigDecimal("5")).blockTime(Duration.ofSeconds(2)).build().maxFeeCapWei()).isEqualByComparingTo(new BigDecimal("5000000000")); }
        @Test void shouldConvertPolygonGweiToWei() { assertThat(EvmChainProperties.builder().chain("polygon").maxFeeCapGwei(new BigDecimal("500")).blockTime(Duration.ofSeconds(2)).build().maxFeeCapWei()).isEqualByComparingTo(new BigDecimal("500000000000")); }
        @Test void shouldThrowWhenChainIsNull() { assertThatThrownBy(() -> EvmChainProperties.builder().chain(null).maxFeeCapGwei(new BigDecimal("200")).blockTime(Duration.ofSeconds(12)).build()).isInstanceOf(NullPointerException.class); }
        @Test void shouldThrowWhenMaxFeeCapGweiIsNull() { assertThatThrownBy(() -> EvmChainProperties.builder().chain("ethereum").maxFeeCapGwei(null).blockTime(Duration.ofSeconds(12)).build()).isInstanceOf(NullPointerException.class); }
        @Test void shouldThrowWhenBlockTimeIsNull() { assertThatThrownBy(() -> EvmChainProperties.builder().chain("ethereum").maxFeeCapGwei(new BigDecimal("200")).blockTime(null).build()).isInstanceOf(NullPointerException.class); }
    }
}
