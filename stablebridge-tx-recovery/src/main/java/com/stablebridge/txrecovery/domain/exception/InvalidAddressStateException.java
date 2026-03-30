package com.stablebridge.txrecovery.domain.exception;

import com.stablebridge.txrecovery.domain.address.model.AddressStatus;

public class InvalidAddressStateException extends StrException {

    public InvalidAddressStateException(String address, String chain,
            AddressStatus currentStatus, AddressStatus targetStatus) {
        super("STR-4002",
                "Cannot transition address %s on chain %s from %s to %s".formatted(
                        address, chain, currentStatus, targetStatus));
    }
}
