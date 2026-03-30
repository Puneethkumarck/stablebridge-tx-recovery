package com.stablebridge.txrecovery.domain.exception;

public class DuplicateAddressException extends StrException {

    public DuplicateAddressException(String address, String chain) {
        super("STR-4091", "Address already registered: %s on chain %s".formatted(address, chain));
    }
}
