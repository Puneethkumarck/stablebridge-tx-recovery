package com.stablebridge.txrecovery.domain.exception;

public class AddressNotFoundException extends StrException {

    public AddressNotFoundException(String address, String chain) {
        super("STR-4042", "Address not found: %s on chain %s".formatted(address, chain));
    }
}
