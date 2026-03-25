package com.stablebridge.txrecovery.domain.model;

public enum StuckReason {
    UNDERPRICED,
    NONCE_GAP,
    NONCE_CONSUMED,
    EXPIRED,
    MEMPOOL_DROPPED,
    NOT_PROPAGATED,
    NOT_SEEN
}
