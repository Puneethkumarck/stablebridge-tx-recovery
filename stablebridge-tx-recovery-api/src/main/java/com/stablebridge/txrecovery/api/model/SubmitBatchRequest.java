package com.stablebridge.txrecovery.api.model;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import lombok.Builder;

@Builder(toBuilder = true)
public record SubmitBatchRequest(
        @NotEmpty @Valid @Size(max = 100) List<SubmitTransactionRequest> transactions) {}
