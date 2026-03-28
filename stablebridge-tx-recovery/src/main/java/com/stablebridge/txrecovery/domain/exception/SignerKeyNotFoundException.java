package com.stablebridge.txrecovery.domain.exception;

public class SignerKeyNotFoundException extends StrException {

    public SignerKeyNotFoundException(String address) {
        super("STR-5030", "No private key found for address: %s".formatted(address));
    }
}
