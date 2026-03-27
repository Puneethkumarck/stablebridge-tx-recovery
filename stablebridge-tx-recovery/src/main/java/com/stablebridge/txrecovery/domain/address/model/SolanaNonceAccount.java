package com.stablebridge.txrecovery.domain.address.model;

import java.util.Objects;

import lombok.Builder;

@Builder(toBuilder = true)
public record SolanaNonceAccount(
        String nonceAccountAddress,
        String authorityAddress,
        String nonceValue,
        NonceAccountStatus status) {

    public SolanaNonceAccount {
        Objects.requireNonNull(nonceAccountAddress);
        Objects.requireNonNull(authorityAddress);
        Objects.requireNonNull(status);
    }
}
