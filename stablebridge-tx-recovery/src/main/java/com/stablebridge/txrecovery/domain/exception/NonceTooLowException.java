package com.stablebridge.txrecovery.domain.exception;

public class NonceTooLowException extends NonRetryableException {

    public NonceTooLowException(long expected, long actual) {
        super("Nonce too low: expected at least %d but got %d".formatted(expected, actual));
    }
}
