package com.stablebridge.txrecovery.domain.exception;

public class NonceAccountNotFoundException extends StrException {

    public NonceAccountNotFoundException(String nonceAccountAddress, String chain) {
        super("STR-5013",
                "Nonce account not found: address=%s chain=%s".formatted(nonceAccountAddress, chain));
    }
}
