package com.stablebridge.txrecovery.domain.exception;

public class CallbackSignerException extends StrException {

    public CallbackSignerException(String detail) {
        super("STR-5032", "Callback signer failed: %s".formatted(detail));
    }

    public CallbackSignerException(String detail, Throwable cause) {
        super("STR-5032", "Callback signer failed: %s".formatted(detail), cause);
    }
}
