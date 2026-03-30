package com.stablebridge.txrecovery.domain.exception;

public class UnknownChainException extends StrException {

    public UnknownChainException(String chain) {
        super("STR-4001", "Unknown chain: %s".formatted(chain));
    }
}
