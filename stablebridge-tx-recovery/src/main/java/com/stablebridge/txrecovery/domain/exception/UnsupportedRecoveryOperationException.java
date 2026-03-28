package com.stablebridge.txrecovery.domain.exception;

public class UnsupportedRecoveryOperationException extends StrException {

    public UnsupportedRecoveryOperationException(String message) {
        super("STR-5040", message);
    }
}
