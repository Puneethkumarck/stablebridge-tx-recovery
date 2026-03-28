package com.stablebridge.txrecovery.infrastructure.signer;

import com.stablebridge.txrecovery.domain.exception.StrException;

public class SignerConfigurationException extends StrException {

    public SignerConfigurationException(String detail) {
        super("STR-5033", "Signer configuration invalid: %s".formatted(detail));
    }
}
