package com.stablebridge.txrecovery.api.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import lombok.Builder;

@Builder(toBuilder = true)
public record ApproveTransactionRequest(
        @NotNull ApprovalActionDto action,
        @NotBlank String reason) {}
