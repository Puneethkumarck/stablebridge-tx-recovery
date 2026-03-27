package com.stablebridge.txrecovery.domain.recovery.model;

import java.util.List;
import java.util.Objects;

import lombok.Builder;

@Builder(toBuilder = true)
public record EscalationPolicy(
        List<EscalationTier> tiers) {

    public EscalationPolicy {
        Objects.requireNonNull(tiers);
        tiers = List.copyOf(tiers);
    }
}
