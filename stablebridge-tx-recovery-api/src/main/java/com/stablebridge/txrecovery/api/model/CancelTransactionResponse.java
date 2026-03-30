package com.stablebridge.txrecovery.api.model;

import lombok.Builder;

@Builder(toBuilder = true)
public record CancelTransactionResponse(
        String transactionId,
        String status,
        String message) {}
