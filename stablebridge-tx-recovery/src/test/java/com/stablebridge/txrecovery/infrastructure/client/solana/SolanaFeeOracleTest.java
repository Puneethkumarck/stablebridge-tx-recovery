package com.stablebridge.txrecovery.infrastructure.client.solana;

import static com.stablebridge.txrecovery.testutil.fixtures.SolanaFeeOracleFixtures.SOME_BLOCK_TIME;
import static com.stablebridge.txrecovery.testutil.fixtures.SolanaFeeOracleFixtures.SOME_CHAIN;
import static com.stablebridge.txrecovery.testutil.fixtures.SolanaFeeOracleFixtures.SOME_EMPTY_FEES;
import static com.stablebridge.txrecovery.testutil.fixtures.SolanaFeeOracleFixtures.SOME_MAX_PRIORITY_FEE_MICRO_LAMPORTS;
import static com.stablebridge.txrecovery.testutil.fixtures.SolanaFeeOracleFixtures.SOME_PRIORITIZATION_FEES;
import static com.stablebridge.txrecovery.testutil.fixtures.SolanaFeeOracleFixtures.SOME_PROGRAM_ADDRESSES;
import static com.stablebridge.txrecovery.testutil.fixtures.SolanaFeeOracleFixtures.SOME_SOLANA_PROPERTIES;
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
class SolanaFeeOracleTest {

    private static final long DEFAULT_COMPUTE_UNITS = 200_000L;
    private static final BigDecimal MICRO_LAMPORTS_PER_LAMPORT = BigDecimal.valueOf(1_000_000L);
    private static final BigDecimal LAMPORTS_PER_SOL = BigDecimal.valueOf(1_000_000_000L);
    private static final MathContext MATH_CONTEXT = MathContext.DECIMAL128;
    private static final int SCALE = 18;
    private static final String CACHE_KEY = "str:gas:cache:solana-mainnet";

    @Mock
    private SolanaRpcClient rpcClient;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    private ObjectMapper objectMapper;
    private SolanaFeeOracle oracle;

    @BeforeEach
    void setUp() {
        objectMapper = JsonMapper.builder().build();
        oracle = new SolanaFeeOracle(rpcClient, SOME_SOLANA_PROPERTIES, redisTemplate, objectMapper);
    }

    private void stubCacheMiss(FeeUrgency urgency) {
        given(redisTemplate.opsForHash()).willReturn(hashOperations);
        given(hashOperations.get(CACHE_KEY, urgency.name())).willReturn(null);
    }

    private BigDecimal computeEstimatedCost(BigDecimal computeUnitPrice) {
        return computeUnitPrice
                .multiply(BigDecimal.valueOf(DEFAULT_COMPUTE_UNITS), MATH_CONTEXT)
                .divide(MICRO_LAMPORTS_PER_LAMPORT, SCALE, RoundingMode.HALF_UP)
                .divide(LAMPORTS_PER_SOL, SCALE, RoundingMode.HALF_UP);
    }

    private FeeEstimate buildExpectedEstimate(FeeUrgency urgency, long computeUnitPriceMicroLamports) {
        var computeUnitPrice = BigDecimal.valueOf(computeUnitPriceMicroLamports)
                .min(BigDecimal.valueOf(SOME_MAX_PRIORITY_FEE_MICRO_LAMPORTS));
        var estimatedCost = computeEstimatedCost(computeUnitPrice);
        return FeeEstimate.builder()
                .computeUnitPrice(computeUnitPrice)
                .estimatedCost(estimatedCost)
                .denomination("SOL")
                .urgency(urgency)
                .details(Map.of(
                        "computeUnitPrice", computeUnitPrice.toPlainString(),
                        "sampleSize", "20",
                        "safetyCapMicroLamports", String.valueOf(SOME_MAX_PRIORITY_FEE_MICRO_LAMPORTS)))
                .build();
    }

    @Nested
    class ChainValidation {

        @Test
        void shouldThrowWhenEstimateCalledWithWrongChain() {
            assertThatThrownBy(() -> oracle.estimate("ethereum", FeeUrgency.FAST))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Oracle for chain solana-mainnet cannot serve chain ethereum");
        }

        @Test
        void shouldThrowWhenEstimateReplacementCalledWithWrongChain() {
            assertThatThrownBy(() -> oracle.estimateReplacement("polygon", "0xoriginal", 1))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Oracle for chain solana-mainnet cannot serve chain polygon");
        }
    }

    @Nested
    class Estimate {

        @Nested
        class SlowUrgency {

            @Test
            void shouldCalculateP50PercentileFee() {
                // given
                stubCacheMiss(FeeUrgency.SLOW);
                given(rpcClient.getRecentPrioritizationFees(SOME_PROGRAM_ADDRESSES))
                        .willReturn(SOME_PRIORITIZATION_FEES);

                // when
                var result = oracle.estimate(SOME_CHAIN, FeeUrgency.SLOW);

                // then
                var expected = buildExpectedEstimate(FeeUrgency.SLOW, 2500L);
                assertThat(result).usingRecursiveComparison().isEqualTo(expected);
            }
        }

        @Nested
        class MediumUrgency {

            @Test
            void shouldCalculateP75PercentileFee() {
                // given
                stubCacheMiss(FeeUrgency.MEDIUM);
                given(rpcClient.getRecentPrioritizationFees(SOME_PROGRAM_ADDRESSES))
                        .willReturn(SOME_PRIORITIZATION_FEES);

                // when
                var result = oracle.estimate(SOME_CHAIN, FeeUrgency.MEDIUM);

                // then
                var expected = buildExpectedEstimate(FeeUrgency.MEDIUM, 6000L);
                assertThat(result).usingRecursiveComparison().isEqualTo(expected);
            }
        }

        @Nested
        class FastUrgency {

            @Test
            void shouldCalculateP90PercentileFee() {
                // given
                stubCacheMiss(FeeUrgency.FAST);
                given(rpcClient.getRecentPrioritizationFees(SOME_PROGRAM_ADDRESSES))
                        .willReturn(SOME_PRIORITIZATION_FEES);

                // when
                var result = oracle.estimate(SOME_CHAIN, FeeUrgency.FAST);

                // then
                var expected = buildExpectedEstimate(FeeUrgency.FAST, 10000L);
                assertThat(result).usingRecursiveComparison().isEqualTo(expected);
            }
        }

        @Nested
        class UrgentUrgency {

            @Test
            void shouldCalculateMaxFeeTimesOnePointFive() {
                // given
                stubCacheMiss(FeeUrgency.URGENT);
                given(rpcClient.getRecentPrioritizationFees(SOME_PROGRAM_ADDRESSES))
                        .willReturn(SOME_PRIORITIZATION_FEES);

                // when
                var result = oracle.estimate(SOME_CHAIN, FeeUrgency.URGENT);

                // then
                var expected = buildExpectedEstimate(FeeUrgency.URGENT, 30000L);
                assertThat(result).usingRecursiveComparison().isEqualTo(expected);
            }
        }
    }

    @Nested
    class SafetyCapEnforcement {

        @Test
        void shouldClampComputeUnitPriceToSafetyCap() {
            // given
            var highFeePropperties = SolanaChainProperties.builder()
                    .chain(SOME_CHAIN)
                    .maxPriorityFeeMicroLamports(100L)
                    .blockTime(SOME_BLOCK_TIME)
                    .programAddresses(SOME_PROGRAM_ADDRESSES)
                    .build();
            var cappedOracle = new SolanaFeeOracle(rpcClient, highFeePropperties, redisTemplate, objectMapper);
            given(redisTemplate.opsForHash()).willReturn(hashOperations);
            given(hashOperations.get("str:gas:cache:solana-mainnet", "FAST")).willReturn(null);
            given(rpcClient.getRecentPrioritizationFees(SOME_PROGRAM_ADDRESSES))
                    .willReturn(SOME_PRIORITIZATION_FEES);

            // when
            var result = cappedOracle.estimate(SOME_CHAIN, FeeUrgency.FAST);

            // then
            var cappedPrice = BigDecimal.valueOf(100L);
            var estimatedCost = computeEstimatedCost(cappedPrice);
            var expected = FeeEstimate.builder()
                    .computeUnitPrice(cappedPrice)
                    .estimatedCost(estimatedCost)
                    .denomination("SOL")
                    .urgency(FeeUrgency.FAST)
                    .details(Map.of(
                            "computeUnitPrice", cappedPrice.toPlainString(),
                            "sampleSize", "20",
                            "safetyCapMicroLamports", "100"))
                    .build();
            assertThat(result).usingRecursiveComparison().isEqualTo(expected);
        }

        @Test
        void shouldNotClampWhenBelowSafetyCap() {
            // given
            stubCacheMiss(FeeUrgency.SLOW);
            given(rpcClient.getRecentPrioritizationFees(SOME_PROGRAM_ADDRESSES))
                    .willReturn(SOME_PRIORITIZATION_FEES);

            // when
            var result = oracle.estimate(SOME_CHAIN, FeeUrgency.SLOW);

            // then
            var expected = buildExpectedEstimate(FeeUrgency.SLOW, 2500L);
            assertThat(result).usingRecursiveComparison().isEqualTo(expected);
            assertThat(result.computeUnitPrice())
                    .isLessThan(BigDecimal.valueOf(SOME_MAX_PRIORITY_FEE_MICRO_LAMPORTS));
        }
    }

    @Nested
    class EstimateReplacement {

        @Test
        void shouldCalculateReplacementFeeWithEscalation() {
            // given
            stubCacheMiss(FeeUrgency.FAST);
            given(rpcClient.getRecentPrioritizationFees(SOME_PROGRAM_ADDRESSES))
                    .willReturn(SOME_PRIORITIZATION_FEES);

            // when
            var result = oracle.estimateReplacement(SOME_CHAIN, "txhash123", 1);

            // then
            var fastPrice = BigDecimal.valueOf(10000L);
            var escalation = new BigDecimal("1.1");
            var escalatedPrice = fastPrice.multiply(escalation, MATH_CONTEXT)
                    .setScale(0, RoundingMode.CEILING);
            var cappedPrice = escalatedPrice.min(BigDecimal.valueOf(SOME_MAX_PRIORITY_FEE_MICRO_LAMPORTS));
            var estimatedCost = computeEstimatedCost(cappedPrice);
            var expected = FeeEstimate.builder()
                    .computeUnitPrice(cappedPrice)
                    .estimatedCost(estimatedCost)
                    .denomination("SOL")
                    .urgency(FeeUrgency.URGENT)
                    .details(Map.of(
                            "baseComputeUnitPrice", fastPrice.toPlainString(),
                            "escalationMultiplier", escalation.toPlainString(),
                            "computeUnitPrice", cappedPrice.toPlainString(),
                            "safetyCapMicroLamports", String.valueOf(SOME_MAX_PRIORITY_FEE_MICRO_LAMPORTS),
                            "attemptNumber", "1"))
                    .build();
            assertThat(result).usingRecursiveComparison().isEqualTo(expected);
        }

        @Test
        void shouldThrowWhenAttemptNumberIsZero() {
            assertThatThrownBy(() -> oracle.estimateReplacement(SOME_CHAIN, "txhash123", 0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("attemptNumber must be >= 1");
        }

        @Test
        void shouldThrowWhenAttemptNumberIsNegative() {
            assertThatThrownBy(() -> oracle.estimateReplacement(SOME_CHAIN, "txhash123", -1))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("attemptNumber must be >= 1");
        }

        @Test
        void shouldApplyHigherEscalationForLaterAttempts() {
            // given
            stubCacheMiss(FeeUrgency.FAST);
            given(rpcClient.getRecentPrioritizationFees(SOME_PROGRAM_ADDRESSES))
                    .willReturn(SOME_PRIORITIZATION_FEES);

            // when
            var result = oracle.estimateReplacement(SOME_CHAIN, "txhash123", 3);

            // then
            var fastPrice = BigDecimal.valueOf(10000L);
            var escalation = new BigDecimal("1.3");
            var escalatedPrice = fastPrice.multiply(escalation, MATH_CONTEXT)
                    .setScale(0, RoundingMode.CEILING);
            var cappedPrice = escalatedPrice.min(BigDecimal.valueOf(SOME_MAX_PRIORITY_FEE_MICRO_LAMPORTS));
            var estimatedCost = computeEstimatedCost(cappedPrice);
            var expected = FeeEstimate.builder()
                    .computeUnitPrice(cappedPrice)
                    .estimatedCost(estimatedCost)
                    .denomination("SOL")
                    .urgency(FeeUrgency.URGENT)
                    .details(Map.of(
                            "baseComputeUnitPrice", fastPrice.toPlainString(),
                            "escalationMultiplier", escalation.toPlainString(),
                            "computeUnitPrice", cappedPrice.toPlainString(),
                            "safetyCapMicroLamports", String.valueOf(SOME_MAX_PRIORITY_FEE_MICRO_LAMPORTS),
                            "attemptNumber", "3"))
                    .build();
            assertThat(result).usingRecursiveComparison().isEqualTo(expected);
        }

        @Test
        void shouldClampReplacementFeeToSafetyCap() {
            // given
            var lowCapProperties = SolanaChainProperties.builder()
                    .chain(SOME_CHAIN)
                    .maxPriorityFeeMicroLamports(5000L)
                    .blockTime(SOME_BLOCK_TIME)
                    .programAddresses(SOME_PROGRAM_ADDRESSES)
                    .build();
            var cappedOracle = new SolanaFeeOracle(rpcClient, lowCapProperties, redisTemplate, objectMapper);
            given(redisTemplate.opsForHash()).willReturn(hashOperations);
            given(hashOperations.get(CACHE_KEY, "FAST")).willReturn(null);
            given(rpcClient.getRecentPrioritizationFees(SOME_PROGRAM_ADDRESSES))
                    .willReturn(SOME_PRIORITIZATION_FEES);

            // when
            var result = cappedOracle.estimateReplacement(SOME_CHAIN, "txhash123", 5);

            // then
            assertThat(result.computeUnitPrice())
                    .isEqualByComparingTo(BigDecimal.valueOf(5000L));
        }
    }

    @Nested
    class CacheBehavior {

        @Test
        void shouldReturnCachedEstimateOnHit() {
            // given
            var cachedEstimate = FeeEstimate.builder()
                    .computeUnitPrice(BigDecimal.valueOf(2500L))
                    .estimatedCost(computeEstimatedCost(BigDecimal.valueOf(2500L)))
                    .denomination("SOL")
                    .urgency(FeeUrgency.SLOW)
                    .details(Map.of("computeUnitPrice", "2500"))
                    .build();
            given(redisTemplate.opsForHash()).willReturn(hashOperations);
            given(hashOperations.get(CACHE_KEY, "SLOW"))
                    .willReturn(objectMapper.writeValueAsString(cachedEstimate));

            // when
            var result = oracle.estimate(SOME_CHAIN, FeeUrgency.SLOW);

            // then
            assertThat(result).usingRecursiveComparison().isEqualTo(cachedEstimate);
            then(rpcClient).should(never()).getRecentPrioritizationFees(SOME_PROGRAM_ADDRESSES);
        }

        @Test
        void shouldFetchFromRpcOnCacheMiss() {
            // given
            stubCacheMiss(FeeUrgency.SLOW);
            given(rpcClient.getRecentPrioritizationFees(SOME_PROGRAM_ADDRESSES))
                    .willReturn(SOME_PRIORITIZATION_FEES);

            // when
            var result = oracle.estimate(SOME_CHAIN, FeeUrgency.SLOW);

            // then
            var expected = buildExpectedEstimate(FeeUrgency.SLOW, 2500L);
            assertThat(result).usingRecursiveComparison().isEqualTo(expected);
            then(rpcClient).should().getRecentPrioritizationFees(SOME_PROGRAM_ADDRESSES);
        }

        @Test
        void shouldSetCacheTtlAfterFetch() {
            // given
            stubCacheMiss(FeeUrgency.SLOW);
            given(rpcClient.getRecentPrioritizationFees(SOME_PROGRAM_ADDRESSES))
                    .willReturn(SOME_PRIORITIZATION_FEES);
            given(redisTemplate.expire(CACHE_KEY, SOME_BLOCK_TIME.toMillis(), TimeUnit.MILLISECONDS))
                    .willReturn(true);

            // when
            oracle.estimate(SOME_CHAIN, FeeUrgency.SLOW);

            // then
            then(redisTemplate).should()
                    .expire(CACHE_KEY, SOME_BLOCK_TIME.toMillis(), TimeUnit.MILLISECONDS);
        }
    }

    @Nested
    class EmptyFeeHandling {

        @Test
        void shouldReturnZeroFeesWhenPrioritizationFeesEmpty() {
            // given
            stubCacheMiss(FeeUrgency.FAST);
            given(rpcClient.getRecentPrioritizationFees(SOME_PROGRAM_ADDRESSES))
                    .willReturn(SOME_EMPTY_FEES);

            // when
            var result = oracle.estimate(SOME_CHAIN, FeeUrgency.FAST);

            // then
            var expected = FeeEstimate.builder()
                    .computeUnitPrice(BigDecimal.ZERO)
                    .estimatedCost(computeEstimatedCost(BigDecimal.ZERO))
                    .denomination("SOL")
                    .urgency(FeeUrgency.FAST)
                    .details(Map.of(
                            "computeUnitPrice", "0",
                            "sampleSize", "0",
                            "safetyCapMicroLamports", String.valueOf(SOME_MAX_PRIORITY_FEE_MICRO_LAMPORTS)))
                    .build();
            assertThat(result).usingRecursiveComparison().isEqualTo(expected);
        }

        @Test
        void shouldReturnZeroFeesForUrgentWhenEmpty() {
            // given
            stubCacheMiss(FeeUrgency.URGENT);
            given(rpcClient.getRecentPrioritizationFees(SOME_PROGRAM_ADDRESSES))
                    .willReturn(SOME_EMPTY_FEES);

            // when
            var result = oracle.estimate(SOME_CHAIN, FeeUrgency.URGENT);

            // then
            assertThat(result.computeUnitPrice()).isEqualByComparingTo(BigDecimal.ZERO);
        }
    }

    @Nested
    class PercentileCalculation {

        @Test
        void shouldReturnZeroForEmptyList() {
            // when
            var result = oracle.computePercentile(List.of(), 50);

            // then
            assertThat(result).isZero();
        }

        @Test
        void shouldReturnSingleElementForSingleElementList() {
            // when
            var result = oracle.computePercentile(List.of(5000L), 50);

            // then
            assertThat(result).isEqualTo(5000L);
        }

        @Test
        void shouldCalculateP50ForEvenSizedList() {
            // given
            var fees = List.of(100L, 200L, 300L, 400L, 500L, 600L, 700L, 800L, 900L, 1000L);

            // when
            var result = oracle.computePercentile(fees, 50);

            // then
            assertThat(result).isEqualTo(500L);
        }

        @Test
        void shouldCalculateP90ForLargeList() {
            // given
            var fees = SOME_PRIORITIZATION_FEES.stream()
                    .map(SolanaPrioritizationFee::prioritizationFee)
                    .sorted()
                    .toList();

            // when
            var result = oracle.computePercentile(fees, 90);

            // then
            assertThat(result).isEqualTo(10000L);
        }

        @Test
        void shouldReturnLastElementForP100() {
            // given
            var fees = List.of(100L, 200L, 300L);

            // when
            var result = oracle.computePercentile(fees, 100);

            // then
            assertThat(result).isEqualTo(300L);
        }

        @Test
        void shouldReturnFirstElementForP1() {
            // given
            var fees = List.of(100L, 200L, 300L);

            // when
            var result = oracle.computePercentile(fees, 1);

            // then
            assertThat(result).isEqualTo(100L);
        }
    }

    @Nested
    class SolanaChainPropertiesTest {

        @Test
        void shouldThrowWhenChainIsNull() {
            assertThatThrownBy(() -> SolanaChainProperties.builder()
                    .chain(null)
                    .maxPriorityFeeMicroLamports(SOME_MAX_PRIORITY_FEE_MICRO_LAMPORTS)
                    .blockTime(SOME_BLOCK_TIME)
                    .programAddresses(SOME_PROGRAM_ADDRESSES)
                    .build())
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        void shouldThrowWhenBlockTimeIsNull() {
            assertThatThrownBy(() -> SolanaChainProperties.builder()
                    .chain(SOME_CHAIN)
                    .maxPriorityFeeMicroLamports(SOME_MAX_PRIORITY_FEE_MICRO_LAMPORTS)
                    .blockTime(null)
                    .programAddresses(SOME_PROGRAM_ADDRESSES)
                    .build())
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        void shouldThrowWhenProgramAddressesIsNull() {
            assertThatThrownBy(() -> SolanaChainProperties.builder()
                    .chain(SOME_CHAIN)
                    .maxPriorityFeeMicroLamports(SOME_MAX_PRIORITY_FEE_MICRO_LAMPORTS)
                    .blockTime(SOME_BLOCK_TIME)
                    .programAddresses(null)
                    .build())
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        void shouldThrowWhenMaxPriorityFeeIsZero() {
            assertThatThrownBy(() -> SolanaChainProperties.builder()
                    .chain(SOME_CHAIN)
                    .maxPriorityFeeMicroLamports(0L)
                    .blockTime(SOME_BLOCK_TIME)
                    .programAddresses(SOME_PROGRAM_ADDRESSES)
                    .build())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("maxPriorityFeeMicroLamports must be positive");
        }

        @Test
        void shouldThrowWhenMaxPriorityFeeIsNegative() {
            assertThatThrownBy(() -> SolanaChainProperties.builder()
                    .chain(SOME_CHAIN)
                    .maxPriorityFeeMicroLamports(-1L)
                    .blockTime(SOME_BLOCK_TIME)
                    .programAddresses(SOME_PROGRAM_ADDRESSES)
                    .build())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("maxPriorityFeeMicroLamports must be positive");
        }
    }
}
