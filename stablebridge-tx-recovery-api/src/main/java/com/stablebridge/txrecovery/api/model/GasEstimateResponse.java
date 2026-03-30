package com.stablebridge.txrecovery.api.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import lombok.Builder;

@Builder(toBuilder = true)
public record GasEstimateResponse(
        String chain,
        BigDecimal baseFee,
        List<GasTierEstimate> tiers,
        Instant updatedAt) {}
