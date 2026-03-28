package com.stablebridge.txrecovery.api.model;

import java.time.Instant;
import java.util.Map;

import lombok.Builder;

@Builder(toBuilder = true)
public record ErrorResponse(
        String errorCode,
        String message,
        Instant timestamp,
        String path,
        Map<String, String> details) {}
