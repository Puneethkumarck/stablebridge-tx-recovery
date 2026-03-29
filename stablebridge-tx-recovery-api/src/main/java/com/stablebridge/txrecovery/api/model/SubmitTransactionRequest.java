package com.stablebridge.txrecovery.api.model;

import java.math.BigDecimal;
import java.util.Map;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import lombok.Builder;

@Builder(toBuilder = true)
public record SubmitTransactionRequest(
        @Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-7[0-9a-fA-F]{3}-[89aAbB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$")
                String intentId,
        @NotBlank String chain,
        @NotBlank String toAddress,
        @NotNull @Positive BigDecimal amount,
        @NotBlank String token,
        int tokenDecimals,
        String tokenContractAddress,
        @Size(max = 20) Map<@Size(max = 64) String, @Size(max = 256) String> metadata) {}
