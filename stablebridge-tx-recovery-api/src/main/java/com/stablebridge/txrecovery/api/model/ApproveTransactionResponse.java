package com.stablebridge.txrecovery.api.model;

import java.time.Instant;

import lombok.Builder;

@Builder(toBuilder = true)
public record ApproveTransactionResponse(
        String transactionId,
        String status,
        String action,
        Instant approvedAt) {}
