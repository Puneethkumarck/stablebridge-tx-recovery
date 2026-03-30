package com.stablebridge.txrecovery.api.model;

import java.util.List;
import java.util.Objects;

import lombok.Builder;

@Builder(toBuilder = true)
public record GasHistoryResponse(
        String chain,
        int hours,
        List<GasHistoryEntry> entries) {

    public GasHistoryResponse {
        Objects.requireNonNull(chain, "chain");
        Objects.requireNonNull(entries, "entries");
        entries = List.copyOf(entries);
    }
}
