package com.stablebridge.txrecovery.api.model;

import lombok.Builder;

@Builder(toBuilder = true)
public record NonceSyncResponse(
        String address,
        String chain,
        long previousNonce,
        long currentNonce) {}
