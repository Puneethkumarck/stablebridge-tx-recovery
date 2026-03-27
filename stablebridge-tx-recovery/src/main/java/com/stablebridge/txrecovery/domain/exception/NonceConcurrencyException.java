package com.stablebridge.txrecovery.domain.exception;

public class NonceConcurrencyException extends StrException {

    public NonceConcurrencyException(String address, String chain) {
        super("STR-5012", "Nonce allocation conflict for address %s on chain %s".formatted(address, chain));
    }
}
