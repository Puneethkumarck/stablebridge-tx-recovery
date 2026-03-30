package com.stablebridge.txrecovery.domain.exception;

public class InvalidTransactionStateException extends StrException {

    public InvalidTransactionStateException(String transactionId, String currentStatus) {
        super("STR-4092",
                "Transaction %s is in %s status, expected AWAITING_HUMAN".formatted(transactionId, currentStatus));
    }
}
