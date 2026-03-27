package com.stablebridge.txrecovery.domain.exception;

public class NoAvailableAddressException extends StrException {

    private static final String ERROR_CODE = "STR-5020";

    public NoAvailableAddressException(String chain, String tier) {
        super(ERROR_CODE, "No available address for chain=%s tier=%s".formatted(chain, tier));
    }
}
