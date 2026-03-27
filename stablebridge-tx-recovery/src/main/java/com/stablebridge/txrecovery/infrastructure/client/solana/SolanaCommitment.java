package com.stablebridge.txrecovery.infrastructure.client.solana;

public enum SolanaCommitment {
    PROCESSED,
    CONFIRMED,
    FINALIZED;

    String value() {
        return name().toLowerCase();
    }
}
