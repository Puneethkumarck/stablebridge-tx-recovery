package com.stablebridge.txrecovery.infrastructure.signer;

import lombok.Builder;

@Builder(toBuilder = true)
record CallbackSignResponse(
        String signedTransactionBytes,
        String transactionHash,
        String signature) {
}
