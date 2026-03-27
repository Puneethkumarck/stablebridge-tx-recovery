package com.stablebridge.txrecovery.infrastructure.client.solana;

import lombok.Builder;

@Builder(toBuilder = true)
record SolanaSignatureStatus(
        Long slot,
        Long confirmations,
        String confirmationStatus,
        Object err) {

    boolean isConfirmedOrFinalized() {
        return "confirmed".equals(confirmationStatus) || "finalized".equals(confirmationStatus);
    }

    boolean isFinalized() {
        return "finalized".equals(confirmationStatus);
    }

    boolean hasError() {
        return err != null;
    }
}
