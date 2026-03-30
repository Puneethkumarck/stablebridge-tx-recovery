package com.stablebridge.txrecovery.domain.transaction.model;

import java.time.Instant;

import com.stablebridge.txrecovery.domain.recovery.model.ApprovalAction;

import lombok.Builder;

@Builder(toBuilder = true)
public record ApprovalResult(
        String transactionId,
        TransactionStatus status,
        ApprovalAction action,
        Instant approvedAt) {}
