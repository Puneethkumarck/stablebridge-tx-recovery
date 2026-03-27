package com.stablebridge.txrecovery.domain.exception;

public class NonceTooLowException extends StrException {

    public NonceTooLowException(long expected, long actual) {
        super("STR-5011", "Nonce too low: expected at least %d but got %d".formatted(expected, actual));
    }
}
