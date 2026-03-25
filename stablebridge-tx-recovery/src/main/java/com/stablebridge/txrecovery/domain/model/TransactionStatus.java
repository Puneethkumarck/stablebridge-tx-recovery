package com.stablebridge.txrecovery.domain.model;

public enum TransactionStatus {
    RECEIVED,
    BUILDING,
    SIGNING,
    SUBMITTED,
    PENDING,
    STUCK,
    RECOVERING,
    AWAITING_HUMAN,
    CONFIRMED,
    FINALIZED,
    DROPPED,
    FAILED,
    CANCELLED
}
