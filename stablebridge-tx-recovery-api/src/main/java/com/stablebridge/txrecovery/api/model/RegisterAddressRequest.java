package com.stablebridge.txrecovery.api.model;

import jakarta.validation.constraints.NotBlank;

import lombok.Builder;

@Builder(toBuilder = true)
public record RegisterAddressRequest(
        @NotBlank String address,
        @NotBlank String chain,
        @NotBlank String tier,
        @NotBlank String signerEndpoint) {}
