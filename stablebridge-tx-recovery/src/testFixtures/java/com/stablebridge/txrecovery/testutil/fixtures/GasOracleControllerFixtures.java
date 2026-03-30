package com.stablebridge.txrecovery.testutil.fixtures;

import static lombok.AccessLevel.PRIVATE;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.stablebridge.txrecovery.api.model.GasEstimateResponse;
import com.stablebridge.txrecovery.api.model.GasTierEstimate;
import com.stablebridge.txrecovery.domain.recovery.model.FeeEstimate;
import com.stablebridge.txrecovery.domain.recovery.model.FeeUrgency;
import com.stablebridge.txrecovery.domain.recovery.model.GasSnapshot;

import lombok.NoArgsConstructor;

@NoArgsConstructor(access = PRIVATE)
public final class GasOracleControllerFixtures {

    public static final String SOME_CHAIN = "ethereum_mainnet";
    public static final String SOME_UNKNOWN_CHAIN = "unknown_chain";
    public static final Instant SOME_UPDATED_AT = Instant.parse("2026-03-30T10:00:00Z");

    public static final BigDecimal SOME_BASE_FEE = new BigDecimal("1000000000");
    public static final BigDecimal SOME_MAX_FEE = new BigDecimal("2000000000");
    public static final BigDecimal SOME_PRIORITY_FEE = new BigDecimal("1500000000");
    public static final BigDecimal SOME_ESTIMATED_COST = new BigDecimal("130000000000000");
    public static final BigDecimal ERC20_GAS = BigDecimal.valueOf(65_000L);

    public static final FeeEstimate SOME_SLOW_ESTIMATE = FeeEstimate.builder()
            .maxFeePerGas(SOME_MAX_FEE)
            .maxPriorityFeePerGas(SOME_PRIORITY_FEE)
            .estimatedCost(SOME_ESTIMATED_COST)
            .denomination("wei")
            .urgency(FeeUrgency.SLOW)
            .details(Map.of("baseFee", SOME_BASE_FEE.toPlainString()))
            .build();

    public static final FeeEstimate SOME_MEDIUM_ESTIMATE = SOME_SLOW_ESTIMATE.toBuilder()
            .urgency(FeeUrgency.MEDIUM)
            .maxFeePerGas(new BigDecimal("2500000000"))
            .maxPriorityFeePerGas(new BigDecimal("2000000000"))
            .build();

    public static final FeeEstimate SOME_FAST_ESTIMATE = SOME_SLOW_ESTIMATE.toBuilder()
            .urgency(FeeUrgency.FAST)
            .maxFeePerGas(new BigDecimal("3000000000"))
            .maxPriorityFeePerGas(new BigDecimal("2500000000"))
            .build();

    public static final FeeEstimate SOME_URGENT_ESTIMATE = SOME_SLOW_ESTIMATE.toBuilder()
            .urgency(FeeUrgency.URGENT)
            .maxFeePerGas(new BigDecimal("5000000000"))
            .maxPriorityFeePerGas(new BigDecimal("4000000000"))
            .build();

    public static final List<FeeEstimate> SOME_ALL_ESTIMATES =
            List.of(SOME_SLOW_ESTIMATE, SOME_MEDIUM_ESTIMATE, SOME_FAST_ESTIMATE, SOME_URGENT_ESTIMATE);

    public static final GasSnapshot SOME_GAS_SNAPSHOT = GasSnapshot.builder()
            .chain(SOME_CHAIN)
            .estimates(SOME_ALL_ESTIMATES)
            .updatedAt(SOME_UPDATED_AT)
            .build();

    public static final GasTierEstimate SOME_SLOW_TIER = GasTierEstimate.builder()
            .urgency("SLOW")
            .maxFeePerGas(SOME_MAX_FEE)
            .maxPriorityFeePerGas(SOME_PRIORITY_FEE)
            .estimatedCostPerTransfer(SOME_MAX_FEE.multiply(ERC20_GAS))
            .build();

    public static final GasEstimateResponse SOME_GAS_ESTIMATE_RESPONSE = GasEstimateResponse.builder()
            .chain(SOME_CHAIN)
            .baseFee(SOME_BASE_FEE)
            .tiers(List.of(
                    SOME_SLOW_TIER,
                    GasTierEstimate.builder()
                            .urgency("MEDIUM")
                            .maxFeePerGas(new BigDecimal("2500000000"))
                            .maxPriorityFeePerGas(new BigDecimal("2000000000"))
                            .estimatedCostPerTransfer(new BigDecimal("2500000000").multiply(ERC20_GAS))
                            .build(),
                    GasTierEstimate.builder()
                            .urgency("FAST")
                            .maxFeePerGas(new BigDecimal("3000000000"))
                            .maxPriorityFeePerGas(new BigDecimal("2500000000"))
                            .estimatedCostPerTransfer(new BigDecimal("3000000000").multiply(ERC20_GAS))
                            .build(),
                    GasTierEstimate.builder()
                            .urgency("URGENT")
                            .maxFeePerGas(new BigDecimal("5000000000"))
                            .maxPriorityFeePerGas(new BigDecimal("4000000000"))
                            .estimatedCostPerTransfer(new BigDecimal("5000000000").multiply(ERC20_GAS))
                            .build()))
            .updatedAt(SOME_UPDATED_AT)
            .build();
}
