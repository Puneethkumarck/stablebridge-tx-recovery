package com.stablebridge.txrecovery.application.controller.gas.mapper;

import java.math.BigDecimal;
import java.util.List;

import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.stablebridge.txrecovery.api.model.GasEstimateResponse;
import com.stablebridge.txrecovery.api.model.GasHistoryEntry;
import com.stablebridge.txrecovery.api.model.GasHistoryResponse;
import com.stablebridge.txrecovery.api.model.GasTierEstimate;
import com.stablebridge.txrecovery.domain.recovery.model.FeeEstimate;
import com.stablebridge.txrecovery.domain.recovery.model.GasSnapshot;

@Mapper(componentModel = "spring", injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface GasOracleControllerMapper {

    BigDecimal ERC20_TRANSFER_GAS = BigDecimal.valueOf(65_000L);

    @Mapping(target = "baseFee", expression = "java(extractBaseFee(snapshot))")
    @Mapping(target = "tiers", expression = "java(toTierEstimates(snapshot.estimates()))")
    GasEstimateResponse toResponse(GasSnapshot snapshot);

    default List<GasTierEstimate> toTierEstimates(List<FeeEstimate> estimates) {
        return estimates.stream().map(this::toTierEstimate).toList();
    }

    default GasTierEstimate toTierEstimate(FeeEstimate estimate) {
        var costPerTransfer = estimate.maxFeePerGas() != null
                ? estimate.maxFeePerGas().multiply(ERC20_TRANSFER_GAS)
                : estimate.estimatedCost();
        return GasTierEstimate.builder()
                .urgency(estimate.urgency().name())
                .maxFeePerGas(estimate.maxFeePerGas())
                .maxPriorityFeePerGas(estimate.maxPriorityFeePerGas())
                .computeUnitPrice(estimate.computeUnitPrice())
                .estimatedCostPerTransfer(costPerTransfer)
                .build();
    }

    default BigDecimal extractBaseFee(GasSnapshot snapshot) {
        if (snapshot.estimates().isEmpty()) {
            return null;
        }
        var first = snapshot.estimates().getFirst();
        var baseFee = first.details().get("baseFee");
        if (baseFee == null) {
            return null;
        }
        try {
            return new BigDecimal(baseFee);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    default GasHistoryResponse toHistoryResponse(String chain, int hours, List<FeeEstimate> estimates) {
        var entries = estimates.stream()
                .map(e -> GasHistoryEntry.builder()
                        .baseFee(e.maxFeePerGas())
                        .avgPriorityFee(e.maxPriorityFeePerGas())
                        .build())
                .toList();
        return GasHistoryResponse.builder()
                .chain(chain)
                .hours(hours)
                .entries(entries)
                .build();
    }
}
