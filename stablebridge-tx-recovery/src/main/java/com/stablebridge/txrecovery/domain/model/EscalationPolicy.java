package com.stablebridge.txrecovery.domain.model;

import java.util.List;
import java.util.Objects;

import lombok.Builder;

@Builder
public record EscalationPolicy(
        List<EscalationTier> tiers) {

    public EscalationPolicy {
        Objects.requireNonNull(tiers);
        tiers = List.copyOf(tiers);
    }
}
