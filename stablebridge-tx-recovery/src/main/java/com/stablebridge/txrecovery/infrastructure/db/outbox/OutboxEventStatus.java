package com.stablebridge.txrecovery.infrastructure.db.outbox;

enum OutboxEventStatus {
    PENDING,
    PUBLISHED,
    FAILED
}
