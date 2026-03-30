package com.stablebridge.txrecovery.api.model;

import lombok.Builder;

@Builder(toBuilder = true)
public record DrainResponse(
        String address,
        String chain,
        String previousStatus,
        String newStatus) {}
