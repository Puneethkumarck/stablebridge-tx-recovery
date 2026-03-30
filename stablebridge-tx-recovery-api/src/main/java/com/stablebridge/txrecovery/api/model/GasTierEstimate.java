package com.stablebridge.txrecovery.api.model;

import java.math.BigDecimal;

import lombok.Builder;

@Builder(toBuilder = true)
public record GasTierEstimate(
        String urgency,
        BigDecimal maxFeePerGas,
        BigDecimal maxPriorityFeePerGas,
        BigDecimal computeUnitPrice,
        BigDecimal estimatedCostPerTransfer) {}
