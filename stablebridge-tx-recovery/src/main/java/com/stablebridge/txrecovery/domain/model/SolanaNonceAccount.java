package com.stablebridge.txrecovery.domain.model;

import java.util.Objects;

import lombok.Builder;

@Builder
public record SolanaNonceAccount(
        String nonceAccountAddress,
        String authorityAddress,
        String nonceValue,
        NonceAccountStatus status) {

    public SolanaNonceAccount {
        Objects.requireNonNull(nonceAccountAddress);
        Objects.requireNonNull(authorityAddress);
        Objects.requireNonNull(nonceValue);
        Objects.requireNonNull(status);
    }
}
