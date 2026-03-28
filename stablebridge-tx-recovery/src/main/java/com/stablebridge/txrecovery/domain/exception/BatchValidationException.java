package com.stablebridge.txrecovery.domain.exception;

public class BatchValidationException extends StrException {

    public BatchValidationException(String reason) {
        super("STR-4002", "Batch validation failed: %s".formatted(reason));
    }
}
