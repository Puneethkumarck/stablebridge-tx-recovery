package com.stablebridge.txrecovery.domain.exception;

public class InvalidChainException extends StrException {

    public InvalidChainException(String chain) {
        super("STR-4001", "Unsupported or disabled chain: %s".formatted(chain));
    }
}
