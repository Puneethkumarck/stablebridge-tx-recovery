package com.stablebridge.txrecovery.application.controller.gas.mapper;

import static com.stablebridge.txrecovery.testutil.fixtures.GasOracleControllerFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mapstruct.factory.Mappers.getMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.stablebridge.txrecovery.api.model.GasHistoryResponse;
import com.stablebridge.txrecovery.api.model.GasTierEstimate;
import com.stablebridge.txrecovery.domain.recovery.model.FeeEstimate;
import com.stablebridge.txrecovery.domain.recovery.model.FeeUrgency;

class GasOracleControllerMapperTest {

    private final GasOracleControllerMapper mapper = getMapper(GasOracleControllerMapper.class);

    @Nested
    class ToResponse {

        @Test
        void shouldMapSnapshotToGasEstimateResponse() {
            // when
            var result = mapper.toResponse(SOME_GAS_SNAPSHOT);

            // then
            var expected = SOME_GAS_ESTIMATE_RESPONSE;
            assertThat(result)
                    .usingRecursiveComparison()
                    .isEqualTo(expected);
        }

        @Test
        void shouldExtractBaseFeeFromDetails() {
            // when
            var result = mapper.toResponse(SOME_GAS_SNAPSHOT);

            // then
            assertThat(result.baseFee()).isEqualByComparingTo(SOME_BASE_FEE);
        }

        @Test
        void shouldReturnNullBaseFeeWhenNotInDetails() {
            // given
            var estimateNoBaseFee = SOME_SLOW_ESTIMATE.toBuilder()
                    .details(Map.of())
                    .build();
            var snapshot = SOME_GAS_SNAPSHOT.toBuilder()
                    .estimates(List.of(estimateNoBaseFee))
                    .build();

            // when
            var result = mapper.toResponse(snapshot);

            // then
            assertThat(result.baseFee()).isNull();
        }

        @Test
        void shouldReturnNullBaseFeeWhenMalformed() {
            // given
            var estimateMalformed = SOME_SLOW_ESTIMATE.toBuilder()
                    .details(Map.of("baseFee", "notANumber"))
                    .build();
            var snapshot = SOME_GAS_SNAPSHOT.toBuilder()
                    .estimates(List.of(estimateMalformed))
                    .build();

            // when
            var result = mapper.toResponse(snapshot);

            // then
            assertThat(result.baseFee()).isNull();
        }

        @Test
        void shouldCalculateEstimatedCostPerTransferUsing65KGas() {
            // when
            var result = mapper.toResponse(SOME_GAS_SNAPSHOT);

            // then
            var firstTier = result.tiers().getFirst();
            var expectedCost = SOME_MAX_FEE.multiply(BigDecimal.valueOf(65_000L));
            assertThat(firstTier.estimatedCostPerTransfer()).isEqualByComparingTo(expectedCost);
        }
    }

    @Nested
    class ToTierEstimate {

        @Test
        void shouldMapFeeEstimateToTierEstimate() {
            // when
            var result = mapper.toTierEstimate(SOME_SLOW_ESTIMATE);

            // then
            var expected = GasTierEstimate.builder()
                    .urgency("SLOW")
                    .maxFeePerGas(SOME_MAX_FEE)
                    .maxPriorityFeePerGas(SOME_PRIORITY_FEE)
                    .estimatedCostPerTransfer(SOME_MAX_FEE.multiply(ERC20_GAS))
                    .build();
            assertThat(result)
                    .usingRecursiveComparison()
                    .isEqualTo(expected);
        }

        @Test
        void shouldUseSolanaComputeUnitPriceWhenMaxFeeIsNull() {
            // given
            var solanaEstimate = FeeEstimate.builder()
                    .computeUnitPrice(new BigDecimal("5000"))
                    .estimatedCost(new BigDecimal("1000000"))
                    .denomination("lamports")
                    .urgency(FeeUrgency.MEDIUM)
                    .build();

            // when
            var result = mapper.toTierEstimate(solanaEstimate);

            // then
            var expected = GasTierEstimate.builder()
                    .urgency("MEDIUM")
                    .computeUnitPrice(new BigDecimal("5000"))
                    .estimatedCostPerTransfer(new BigDecimal("1000000"))
                    .build();
            assertThat(result)
                    .usingRecursiveComparison()
                    .isEqualTo(expected);
        }
    }

    @Nested
    class ToHistoryResponse {

        @Test
        void shouldMapEmptyHistoryToEmptyResponse() {
            // when
            var result = mapper.toHistoryResponse(SOME_CHAIN, 24, List.of());

            // then
            var expected = GasHistoryResponse.builder()
                    .chain(SOME_CHAIN)
                    .hours(24)
                    .entries(List.of())
                    .build();
            assertThat(result)
                    .usingRecursiveComparison()
                    .isEqualTo(expected);
        }
    }
}
