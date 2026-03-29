package com.stablebridge.txrecovery.domain.exception;

public class TransactionNotFoundException extends StrException {

    public TransactionNotFoundException(String transactionId) {
        super("STR-4041", "Transaction not found: %s".formatted(transactionId));
    }
}
