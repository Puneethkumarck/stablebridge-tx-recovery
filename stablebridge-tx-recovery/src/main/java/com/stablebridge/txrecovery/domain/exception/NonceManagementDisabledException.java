package com.stablebridge.txrecovery.domain.exception;

public class NonceManagementDisabledException extends StrException {

    public NonceManagementDisabledException() {
        super("STR-4091", "Nonce management is not enabled");
    }
}
