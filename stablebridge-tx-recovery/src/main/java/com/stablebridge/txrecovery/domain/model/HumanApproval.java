package com.stablebridge.txrecovery.domain.model;

import java.time.Instant;
import java.util.Objects;

import lombok.Builder;

@Builder
public record HumanApproval(
        ApprovalAction action,
        String approvedBy,
        String reason,
        Instant approvedAt) {

    public HumanApproval {
        Objects.requireNonNull(action);
        Objects.requireNonNull(approvedBy);
    }
}
