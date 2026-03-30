package com.stablebridge.txrecovery.api.model;

import java.time.Instant;

import lombok.Builder;

@Builder(toBuilder = true)
public record AddressResponse(
        String id,
        String address,
        String chain,
        String chainFamily,
        String tier,
        String status,
        long currentNonce,
        int inFlightCount,
        String signerEndpoint,
        Instant registeredAt,
        Instant retiredAt,
        Instant lastUsedAt) {}
