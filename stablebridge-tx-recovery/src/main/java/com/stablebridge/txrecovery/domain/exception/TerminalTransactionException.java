package com.stablebridge.txrecovery.domain.exception;

public class TerminalTransactionException extends StrException {

    public TerminalTransactionException(String transactionId, String currentStatus) {
        super("STR-4093",
                "Transaction %s is in terminal status %s and cannot be cancelled".formatted(
                        transactionId, currentStatus));
    }
}
