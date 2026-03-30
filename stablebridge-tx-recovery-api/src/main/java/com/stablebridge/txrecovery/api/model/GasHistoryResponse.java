package com.stablebridge.txrecovery.api.model;

import java.util.List;

import lombok.Builder;

@Builder(toBuilder = true)
public record GasHistoryResponse(
        String chain,
        int hours,
        List<GasHistoryEntry> entries) {}
