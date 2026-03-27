package com.stablebridge.txrecovery.domain.exception;

public class NonRetryableException extends StrException {

    public NonRetryableException(String message) {
        super("STR-5010", message);
    }

    public NonRetryableException(String message, Throwable cause) {
        super("STR-5010", message, cause);
    }
}
