package com.stablebridge.txrecovery.domain.exception;

public class SignatureComputationException extends StrException {

    public SignatureComputationException(String detail) {
        super("STR-5031", "Signature computation failed: %s".formatted(detail));
    }

    public SignatureComputationException(String detail, Throwable cause) {
        super("STR-5031", "Signature computation failed: %s".formatted(detail), cause);
    }
}
