package com.stablebridge.txrecovery.domain.exception;

public class ChainNotFoundException extends StrException {

    public ChainNotFoundException(String chain) {
        super("STR-4041", "Chain not found or disabled: %s".formatted(chain));
    }
}
