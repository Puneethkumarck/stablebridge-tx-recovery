package com.stablebridge.txrecovery.api.model;

import jakarta.validation.constraints.NotBlank;

import lombok.Builder;

@Builder(toBuilder = true)
public record CancelTransactionRequest(
        @NotBlank String reason) {}
