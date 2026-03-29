package com.stablebridge.txrecovery.domain.exception;

import lombok.Getter;

@Getter
public class DuplicateIntentException extends StrException {

    private final String existingTransactionId;

    public DuplicateIntentException(String existingTransactionId) {
        super("STR-4091", "Duplicate intent: existing transaction %s".formatted(existingTransactionId));
        this.existingTransactionId = existingTransactionId;
    }
}
